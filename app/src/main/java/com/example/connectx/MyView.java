package com.example.connectx;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Map;

public class MyView extends View {

    private Map<String, Boolean> gameMap;
    private Paint playerOnePaint;
    private Paint playerTwoPaint;
    private Paint backgroundPaint;
    private Paint shadowPaint;
    private Paint whitePaint;
    private int mapHeight;
    private int mapWidth;
    private float slotSize;

    public MyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setGameMap(Map<String, Boolean> gameMap) {
        this.gameMap = gameMap;
    }

    public void init(int mapWidth, int mapHeight, float slotSize) {
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.slotSize = slotSize;

        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(Color.WHITE);

        playerOnePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerOnePaint.setStyle(Paint.Style.FILL);
        playerOnePaint.setColor(0xff51938d);

        playerTwoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerTwoPaint.setStyle(Paint.Style.FILL);
        playerTwoPaint.setColor(0xff690059);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.LTGRAY);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0xff101010);
        shadowPaint.setMaskFilter(new BlurMaskFilter(8, BlurMaskFilter.Blur.NORMAL));
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (gameMap != null) {
            int border = 8;
            canvas.drawRect(new RectF(0, slotSize, canvas.getWidth(), slotSize * (mapHeight + 1)),
                    shadowPaint);
            canvas.drawRect(new RectF(border,
                            border + slotSize,
                            canvas.getWidth() - border,
                            slotSize * (mapHeight + 1) - border),
                    backgroundPaint);

            int offset = 10;
            for (int i = 1; i <= mapWidth; i++) {
                for (int j = 1; j <= mapHeight; j++) {

                    // draw outline
                    canvas.drawCircle((i - 1) * slotSize + slotSize / 2,
                            j * slotSize + slotSize / 2,
                            (float) (slotSize / 2 * 0.85),
                            shadowPaint);

                    // draw circle
                    if (gameMap.get("m" + i + j) == null) {
                        canvas.drawCircle((i - 1) * slotSize + slotSize / 2,
                                j * slotSize + slotSize / 2,
                                (float) (slotSize / 2 * 0.78),
                                whitePaint);
                    } else {
                        if (gameMap.get("m" + i + j)) {
                            canvas.drawCircle((i - 1) * slotSize + slotSize / 2,
                                    j * slotSize + slotSize / 2,
                                    (float) (slotSize / 2 * 0.78),
                                    playerOnePaint);
                        } else {
                            canvas.drawCircle((i - 1) * slotSize + slotSize / 2,
                                    j * slotSize + slotSize / 2,
                                    (float) (slotSize / 2 * 0.78),
                                    playerTwoPaint);
                        }
                    }
                }
            }
        }
    }


}
