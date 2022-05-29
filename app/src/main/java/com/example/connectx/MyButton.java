package com.example.connectx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;

public class MyButton extends androidx.appcompat.widget.AppCompatButton {

    private Boolean hover_player1;
    private Paint black;
    private Paint default_color;
    private Paint playerTwoColor;
    private Paint playerOneColor;

    public MyButton(Context context) {
        super(context);
    }
    public MyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void init(){
        hover_player1 = null;

        black = new Paint(Paint.ANTI_ALIAS_FLAG);
        black.setColor(Color.BLACK);

        default_color = new Paint(Paint.ANTI_ALIAS_FLAG);
        default_color.setColor(Color.LTGRAY);

        playerOneColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerOneColor.setStyle(Paint.Style.FILL);
        playerOneColor.setColor(0xff51938d);

        playerTwoColor = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerTwoColor.setStyle(Paint.Style.FILL);
        playerTwoColor.setColor(0xff690059);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(canvas.getWidth() / 2,
                canvas.getHeight() / 2,
                (float) (canvas.getHeight() / 2 * 0.85),
                black);

        if(hover_player1 == null){
            canvas.drawCircle(canvas.getWidth() / 2,
                    canvas.getHeight() / 2,
                    (float) (canvas.getHeight() / 2 * 0.78),
                    default_color);
        }else{
            if(hover_player1){
                canvas.drawCircle(canvas.getWidth() / 2,
                        canvas.getHeight() / 2,
                        (float) (canvas.getHeight() / 2 * 0.78),
                        playerOneColor);

            }else{
                canvas.drawCircle(canvas.getWidth() / 2,
                        canvas.getHeight() / 2,
                        (float) (canvas.getHeight() / 2 * 0.78),
                        playerTwoColor);
            }
        }
    }

    public void setHoverColor(Boolean hover_player1) {
        this.hover_player1 = hover_player1;
    }
}