package com.example.connectx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class RectView extends View {

    public RectView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint color = new Paint(Paint.ANTI_ALIAS_FLAG);
        color.setStyle(Paint.Style.FILL);
        color.setColor(Color.LTGRAY);

        Paint black = new Paint(Paint.ANTI_ALIAS_FLAG);
        black.setStyle(Paint.Style.FILL);
        black.setColor(Color.BLACK);

        int border = 10;
        canvas.drawRect(new RectF(0,0,canvas.getWidth(), canvas.getHeight()), black);
        canvas.drawRect(new RectF(border,border,canvas.getWidth()-border, canvas.getHeight()-border), color);
    }

}
