package com.example.connectx;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private int kiLevel;
    private int mapHeight;
    private int mapWidth;
    private int numX;
    private boolean kiActive;
    private boolean gameOver;
    private boolean turnPlayer1;
    private ArrayList<String[]> combos;
    private TextView tw_title;
    private TextView tw_kiActive;
    private TextView tw_currentPlayer;
    private Map<String, Boolean> gameMap;
    private String[] indices;
    private Map<String, Integer> indicesMap;
    private LinearLayout buttonContainer;
    private MyView myView;
    private int border;
    private boolean mainMenu;
    private Button kiLevelButton;
    private Button startButton;
    private Button resetButton;
    private Button twoPlayerButton;
    private ConstraintLayout inputs;
    private ConstraintLayout layout;
    private Thread myThread;
    private MyButton[] myButtons;

    private void start() {
        gameOver = false;
        tw_title.setText(String.format("Connect %d", numX));
        combos = genCombos(mapHeight, mapWidth, numX);
        genMap(mapHeight, mapWidth);

        // choose random Player
        if (new Random().nextInt(2) == 0) {
            turnPlayer1 = true;
        } else {
            turnPlayer1 = false;
        }
        setTextCurrentPlayer();
        changeUI();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myView = findViewById(R.id.gameview);
        buttonContainer = findViewById(R.id.buttonContainer);
        inputs = findViewById(R.id.inputs);
        layout = findViewById(R.id.layout);

        tw_title = findViewById(R.id.title);
        tw_kiActive = findViewById(R.id.kiActive);
        tw_currentPlayer = findViewById(R.id.currentPlayer);

        kiLevelButton = findViewById(R.id.kiLevel);
        resetButton = findViewById(R.id.reset);
        twoPlayerButton = findViewById(R.id.twoPlayer);
        startButton = findViewById(R.id.start);

        EditText inputHeight = findViewById(R.id.height);
        EditText inputWidth = findViewById(R.id.width);
        EditText inputNumX = findViewById(R.id.numx);

        EditText[] myEditTexts = {inputHeight, inputWidth, inputNumX};
        layout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                removeFokus(myEditTexts, v, event);
            }
            return false;
        });

        kiActive = false;
        kiLevel = 0;
        setTextKIActive();
        setTextKILevel();

        border = 50;
        mainMenu = true;

        tw_title.setText("Connect X");

        Runnable myRunnable = new Runnable(){
            @Override
            public void run() {
                int state = 0;
                int max_state = 10;
                boolean up = true;
                while(!mainMenu) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (up){
                        state++;
                    } else {
                        state--;
                    }
                    if (state >= max_state){
                        up = false;
                    } else if (state <= 0){
                        up = true;
                    }
                    for(MyButton myButton: myButtons){
                        myButton.setState(state);
                        myButton.invalidate();
                    }
                }
            }
        };

        startButton.setOnClickListener(view -> {
            mapHeight = Integer.parseInt(inputHeight.getText().toString());
            mapWidth = Integer.parseInt(inputWidth.getText().toString());
            numX = Integer.parseInt(inputNumX.getText().toString());
            removeFokus(myEditTexts, view, null);
            mainMenu = false;
            start();
            initViews();
            myView.invalidate();
            changeUI();
            maybeMoveFromKI();
            myThread = new Thread(myRunnable);
            myThread.start();
        });

        resetButton.setOnClickListener(view -> {
            tw_title.setText("Connect X");
            mainMenu = true;
            changeUI();
        });

        twoPlayerButton.setOnClickListener(view -> {
            kiActive = !kiActive;
            setTextKIActive();
            maybeMoveFromKI();
        });

        kiLevelButton.setOnClickListener(view -> {
            kiLevel++;
            kiLevel %= 1;
            setTextKILevel();
            setTextKIActive();
        });

    }

    private void genMap(int height, int width) {
        gameMap = new HashMap<String, Boolean>();
        indices = new String[height * width];
        indicesMap = new HashMap<String, Integer>();
        int i = 0;
        for (int h = 1; h <= height; h++) {
            for (int w = 1; w <= width; w++) {
                gameMap.put("m" + h + w, null);
                indices[i] = "m" + h + w;
                indicesMap.put(indices[i], i);
                i++;
            }
        }
    }

    private void changeUI() {
        if (mainMenu) {
            startButton.setVisibility(View.VISIBLE);
            myView.setVisibility(View.INVISIBLE);
            resetButton.setVisibility(View.INVISIBLE);
            kiLevelButton.setVisibility(View.INVISIBLE);
            twoPlayerButton.setVisibility(View.INVISIBLE);
            tw_kiActive.setVisibility(View.INVISIBLE);
            tw_currentPlayer.setVisibility(View.INVISIBLE);
            buttonContainer.setVisibility(View.INVISIBLE);
            inputs.setVisibility(View.VISIBLE);
            buttonContainer.setVisibility(View.INVISIBLE);
        } else {
            startButton.setVisibility(View.INVISIBLE);
            myView.setVisibility(View.VISIBLE);
            resetButton.setVisibility(View.VISIBLE);
            kiLevelButton.setVisibility(View.VISIBLE);
            twoPlayerButton.setVisibility(View.VISIBLE);
            tw_kiActive.setVisibility(View.VISIBLE);
            tw_currentPlayer.setVisibility(View.VISIBLE);
            buttonContainer.setVisibility(View.VISIBLE);
            inputs.setVisibility(View.INVISIBLE);
            buttonContainer.setVisibility(View.VISIBLE);

        }
    }

    public void initViews() {
        int gameHeight = myView.getMeasuredHeight();
        int gameWidth = myView.getMeasuredWidth();

        int minSlotWidth = gameWidth / mapWidth;
        int minSlotHeight = gameHeight / mapHeight;
        int slotSize = 0;
        if (minSlotWidth < minSlotHeight) {
            slotSize = minSlotWidth;
        } else {
            slotSize = minSlotHeight;
        }

        Bitmap myBitmap = Bitmap.createBitmap(gameWidth, gameHeight, Bitmap.Config.RGBA_F16);

        myButtons = new MyButton[mapWidth];
        buttonContainer.removeAllViews();
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.width = slotSize;
        params.height = params.width;
        for (int i = 1; i <= mapWidth; i++) {
            MyButton button = new MyButton(this);
            button.init();
            button.setLayoutParams(params);
            button.setBackground(null);
            buttonContainer.addView(button);
            final int column = i;
            button.setOnClickListener(view -> {
                if (!gameOver && addElemToGameMap(column)) {
                    isGameOver(indices);
                    changeTurn();
                    myView.invalidate();
                }
            });

            button.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {

                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        //Button Pressed
                        button.setHoverColor(turnPlayer1);
                        button.invalidate();
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        //finger was lifted
                        button.setHoverColor(null);
                        button.invalidate();
                    }

                    return false;
                }
            });
            myButtons[i-1] = button;
        }

        myView.init(mapWidth, mapHeight, slotSize);
        myView.setGameMap(gameMap);
        myView.draw(new Canvas(myBitmap));
    }

    private void removeFokus(EditText[] myEditTexts, View v, MotionEvent event) {
        for (EditText mEditText : myEditTexts) {
            if (mEditText.isFocused()) {
                if (event == null) {
                    mEditText.clearFocus();
                    InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                } else {
                    Rect outRect = new Rect();
                    mEditText.getGlobalVisibleRect(outRect);
                    if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                        mEditText.clearFocus();
                        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
    }

    public boolean addElemToGameMap(int column) {
        boolean success = false;
        for (int j = mapHeight; j > 0; j--) {
            String idx = "m" + column + j;
            if (gameMap.get(idx) == null) {
                gameMap.put(idx, turnPlayer1);
                return true;
            }
        }
        return success;
    }

    private void changeTurn() {
        turnPlayer1 = !turnPlayer1;
        setTextCurrentPlayer();
    }

    public ArrayList<String[]> genCombos(int height, int width, int x) {
        ArrayList<String[]> combos = new ArrayList<>();
        // diagonale von oben links nach unten rechts
        for (int i = 1; i <= width - (x - 1); i++) {
            for (int j = 1; j <= height - (x - 1); j++) {
                String[] combo = new String[x];
                for (int k = 0; k < x; k++) {
                    int _j = j + k;
                    int _i = i + k;
                    combo[k] = "m" + _i + _j;
                }
                combos.add(combo);
            }
        }
        // diagonale von unten links nach oben rechts
        for (int i = 1; i <= width - (x - 1); i++) {
            for (int j = x; j <= height; j++) {
                String[] combo = new String[x];
                for (int k = 0; k < x; k++) {
                    int _j = j - k;
                    int _i = i + k;
                    combo[k] = "m" + _i + _j;
                }
                combos.add(combo);
            }
        }
        // vertikale
        for (int i = 1; i <= width; i++) {
            for (int j = 1; j <= height - (x - 1); j++) {
                String[] combo = new String[x];
                for (int k = 0; k < x; k++) {
                    int _j = j + k;
                    combo[k] = "m" + i + _j;
                }
                combos.add(combo);
            }
        }
        // horizontale
        for (int i = 1; i <= width - (x - 1); i++) {
            for (int j = 1; j <= height; j++) {
                String[] combo = new String[x];
                for (int k = 0; k < x; k++) {
                    int _i = i + k;
                    combo[k] = "m" + _i + j;
                }
                combos.add(combo);
            }
        }
        return combos;
    }

    private void isGameOver(String[] indices) {
        for (String[] combo : combos) {
            //check for null
            boolean containsNull = false;
            for (int i = 0; i < combo.length; i++) {
                if (gameMap.get(combo[i]) == null) {
                    containsNull = true;
                }
            }
            // no null value in combo
            if (!containsNull) {
                int p1 = 0;
                int p2 = 0;
                for (int i = 0; i < combo.length; i++) {
                    if (gameMap.get(combo[i])) {
                        p1++;
                    } else {
                        p2++;
                    }
                }
                if (p1 == 0) {
                    if (kiActive) {
                        gameOver("Red (KI) Wins");
                    } else {
                        gameOver("Red Wins");
                    }
                } else if (p2 == 0) {
                    gameOver("Green Wins");
                }
            }
        }
        if (!gameOver) {
            //check for draw
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                if (gameMap.get(indices[i]) == null) {
                    sum++;
                }
            }
            if (sum == 0) {
                gameOver("Draw");
            }
        }
    }

    private void gameOver(String s) {
        tw_title.setText(s);
        gameOver = true;
        setTextCurrentPlayer();

        buttonContainer.setVisibility(View.INVISIBLE);
    }

    private void maybeMoveFromKI() {
        if (!gameOver && !turnPlayer1 && kiActive) {
            new CountDownTimer(250 + new Random().nextInt(720), 1000) {

                @Override
                public void onTick(long arg0) {
                }

                @Override
                public void onFinish() {
                    moveFromKI(kiLevel);
                    isGameOver(indices);
                    changeTurn();
                    setTextCurrentPlayer();
                }
            }.start();
        }
    }

    private void moveFromKI(int kiLevel) {

    }

    private int canWin(int[] scores) {
        // first rate all combos
        int combo_i = -1;
        for (int i = 0; i < scores.length; i++) {
            //   pick one with score x-1
            if (scores[i] == numX - 1) {
                combo_i = i;
            }
        }
        return combo_i;
    }

    private Integer randomPossibleMove(String[] indices) {
        //TODO
        return 0;
    }

    private String chooseElement(String[] choosenCombo) {
        //TODO
        return "";
    }

    private int[] get_scores(boolean bot, Map<String, Boolean> map) {
        int[] scores = new int[combos.size()];
        for (int i = 0; i < combos.size(); i++) {
            int score = 0;
            String[] combo = combos.get(i);
            for (String elem : combo) {
                if (map.get(elem) != null) {
                    if (bot) {
                        if (map.get(elem)) {
                            score = -99;
                            continue;
                        } else {
                            score++;
                        }
                    } else {
                        if (!map.get(elem)) {
                            score = -99;
                            continue;
                        } else {
                            score++;
                        }

                    }
                }
            }
            scores[i] = score;
        }
        if (bot) {
            System.out.println("BOT " + Arrays.toString(scores));
        } else {
            System.out.println("VS " + Arrays.toString(scores));
        }
        return scores;
    }

    private int sum(int[] scores) {
        int sum = 0;
        for (int score : scores) {
            sum += score;
        }
        return sum;
    }

    private void setTextCurrentPlayer() {
        if (gameOver) {
            tw_currentPlayer.setText("");
        } else {
            if (turnPlayer1) {
                tw_currentPlayer.setText("Waiting for Green");
            } else if (kiActive) {
                tw_currentPlayer.setText("Waiting for Red (KI)");
            } else {
                tw_currentPlayer.setText("Waiting for Red");
            }
        }
    }

    private void setTextKILevel() {
        kiLevelButton.setText("" + kiLevel);
    }

    private void setTextKIActive() {
        if (kiActive) {
            tw_kiActive.setText("active lvl " + kiLevel);
        } else {
            tw_kiActive.setText("inactive");
        }
    }

}