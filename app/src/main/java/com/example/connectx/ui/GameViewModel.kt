package com.example.connectx.ui

import android.app.Application
import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.connectx.R
import com.example.connectx.model.GameConfig
import com.example.connectx.model.GameEngine
import com.example.connectx.model.GameState
import com.example.connectx.model.Player
import com.example.connectx.model.PlayerAppearance
import com.example.connectx.model.SetupSettings
import com.example.connectx.model.appearanceFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private var engine: GameEngine? = null

    private val _config = MutableLiveData<GameConfig>(GameConfig.create(7, 6, 4))
    val config: MutableLiveData<GameConfig> = _config

    private val _gameState = MutableLiveData<GameState>(GameState.Configuring(GameConfig.create(7, 6, 4)))
    val gameState: MutableLiveData<GameState> = _gameState

    /** The column the AI is previewing (null when not aiming). */
    private val _aiPreviewColumn = MutableLiveData<Int?>(null)
    val aiPreviewColumn: LiveData<Int?> = _aiPreviewColumn

    private var aiJob: Job? = null

    /** Persisted user settings (board size, AI, ...). */
    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("connectx", Context.MODE_PRIVATE)
    }

    companion object {
        /** How long the AI "thinks" before its preview appears. */
        const val AI_THINK_DELAY_MS = 500L
        /** How long the AI's preview hovers before it plays. */
        const val AI_PREVIEW_DELAY_MS = 400L

        /** Disc icon choices, in picker order (2 rows of 6). */
        val DISC_ICONS = intArrayOf(
            R.drawable.ic_disc_star_up,
            R.drawable.ic_disc_star_down,
            R.drawable.ic_disc_a,
            R.drawable.ic_disc_plus,
            R.drawable.ic_disc_x,
            R.drawable.ic_disc_diamond,
            R.drawable.ic_disc_triangle_up,
            R.drawable.ic_disc_triangle_down,
            R.drawable.ic_disc_square,
            R.drawable.ic_disc_heart,
            R.drawable.ic_disc_hexagon,
            R.drawable.ic_disc_ring
        )

        /** Disc color palette, in picker order (3 rows of 8). */
        val PALETTE = intArrayOf(
            R.color.palette0,
            R.color.palette1,
            R.color.palette2,
            R.color.palette3,
            R.color.palette4,
            R.color.palette5,
            R.color.palette6,
            R.color.palette7,
            R.color.palette8,
            R.color.palette9,
            R.color.palette10,
            R.color.palette11,
            R.color.palette12,
            R.color.palette13,
            R.color.palette14,
            R.color.palette15,
            R.color.palette16,
            R.color.palette17,
            R.color.palette18,
            R.color.palette19,
            R.color.palette20,
            R.color.palette21,
            R.color.palette22,
            R.color.palette23,
            R.color.palette24,
            R.color.palette25,
            R.color.palette26,
            R.color.palette27,
            R.color.palette28,
            R.color.palette29,
            R.color.palette30,
            R.color.palette31
        )
    }

    init {
        _config.value = buildConfig(loadSettings())
    }

    // ---------------------------------------------------------------------------------------------
    // Persisted settings
    // ---------------------------------------------------------------------------------------------

    fun loadSettings(): SetupSettings {
        return SetupSettings(
            width = prefs.getInt("width", SetupSettings.DEFAULT.width),
            height = prefs.getInt("height", SetupSettings.DEFAULT.height),
            connect = prefs.getInt("connect", SetupSettings.DEFAULT.connect),
            vsAI = prefs.getBoolean("vsAI", SetupSettings.DEFAULT.vsAI),
            aiLevel = prefs.getInt("aiLevel", SetupSettings.DEFAULT.aiLevel),
            icon1 = prefs.getInt("icon1", -1),
            icon2 = prefs.getInt("icon2", -1),
            color1 = prefs.getInt("color1", -1),
            color2 = prefs.getInt("color2", -1)
        )
    }

    fun saveSettings(settings: SetupSettings) {
        prefs.edit()
            .putInt("width", settings.width)
            .putInt("height", settings.height)
            .putInt("connect", settings.connect)
            .putBoolean("vsAI", settings.vsAI)
            .putInt("aiLevel", settings.aiLevel)
            .putInt("icon1", settings.icon1)
            .putInt("icon2", settings.icon2)
            .putInt("color1", settings.color1)
            .putInt("color2", settings.color2)
            .apply()
    }

    /**
     * Builds a config from the full setup. Disc colors come from the palette
     * (light/dark variants are blended from the base), icons from the picker.
     * The AI plays with the second player's appearance.
     */
    private fun buildConfig(setup: SetupSettings): GameConfig {
        val color1 = setup.color1.coerceAtLeast(0)
        val color2 = setup.color2.coerceAtLeast(0)
        val icon1 = setup.icon1.coerceAtLeast(0)
        val icon2 = SetupSettings.resolveIcon2(setup.icon2, setup.vsAI)
        val player2 = appearanceForChoice(color2, icon2)
        return GameConfig.create(
            width = setup.width,
            height = setup.height,
            connect = setup.connect,
            vsAI = setup.vsAI,
            aiLevel = setup.aiLevel,
            player1Appearance = appearanceForChoice(color1, icon1),
            player2Appearance = player2,
            aiAppearance = player2
        )
    }

    private fun appearanceForChoice(paletteIndex: Int, iconIndex: Int): PlayerAppearance {
        val ctx = getApplication<Application>()
        val palette = PALETTE
        val base = ContextCompat.getColor(ctx, palette[paletteIndex.coerceIn(0, palette.size - 1)])
        return PlayerAppearance(
            color = base,
            lightColor = blend(base, Color.WHITE, 0.45f),
            darkColor = blend(base, Color.BLACK, 0.40f),
            iconRes = DISC_ICONS[iconIndex.coerceIn(0, DISC_ICONS.size - 1)]
        )
    }

    /** Linear blend of [base] toward [other] by [fraction] (0..1). */
    private fun blend(base: Int, other: Int, fraction: Float): Int {
        fun mix(a: Int, b: Int) = a + ((b - a) * fraction)
        return Color.rgb(
            mix(Color.red(base), Color.red(other)).toInt(),
            mix(Color.green(base), Color.green(other)).toInt(),
            mix(Color.blue(base), Color.blue(other)).toInt()
        )
    }

    fun updateConfig(setup: SetupSettings) {
        _config.value = buildConfig(setup)
    }

    fun startGame() {
        aiJob?.cancel()
        _aiPreviewColumn.value = null
        val cfg = _config.value ?: return
        engine = GameEngine.create(cfg)
        _gameState.value = engine?.createInitialState()
    }

    /**
     * Human move. Ignored while the AI is thinking.
     */
    fun makeMove(column: Int) {
        val eng = engine ?: return
        val state = _gameState.value as? GameState.Playing ?: return
        if (config.value?.vsAI == true && state.currentPlayer == Player.Player2) return

        val newState = eng.makeMove(state, column) ?: return
        _gameState.value = newState
    }

    /**
     * Runs the AI move with a visible sequence: "thinking" pause, then the
     * preview (balloon) hovers over the target column, then the move.
     */
    fun requestAIMove() {
        val state = _gameState.value as? GameState.Playing ?: return
        val cfg = _config.value ?: return
        if (!cfg.vsAI || state.currentPlayer != Player.Player2) return
        if (aiJob?.isActive == true) return

        aiJob = viewModelScope.launch {
            delay(AI_THINK_DELAY_MS)
            if (_gameState.value !== state) return@launch

            val column = engine?.getAIMove(state) ?: return@launch
            _aiPreviewColumn.value = column

            delay(AI_PREVIEW_DELAY_MS)
            if (_gameState.value !== state) return@launch

            val newState = engine?.makeMove(state, column) ?: return@launch
            // Apply the move first (the board captures the balloon position for
            // the flight), then retire the preview
            _gameState.value = newState
            _aiPreviewColumn.value = null
        }
    }

    fun resetGame() {
        aiJob?.cancel()
        _aiPreviewColumn.value = null
        val cfg = _config.value ?: return
        engine = GameEngine.create(cfg)
        _gameState.value = engine?.createInitialState()
    }

    fun appearanceForCurrentPlayer(): PlayerAppearance? {
        val player = (_gameState.value as? GameState.Playing)?.currentPlayer ?: return null
        return _config.value?.appearanceFor(player)
    }
}
