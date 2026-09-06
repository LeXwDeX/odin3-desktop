package com.odin.desktop.validationtarget;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
public class TargetActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().hasExtra("orientation")) {
            setRequestedOrientation(getIntent().getIntExtra("orientation", -1));
        }
        setContentView(new View(this) {
            final Paint paint = new Paint();
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(Color.rgb(220, 220, 220));
                paint.setColor(Color.rgb(40, 150, 230));
                canvas.drawRect(100, 200, 700, 800, paint);
                paint.setColor(Color.BLACK); paint.setTextSize(44);
                canvas.drawText("Orientation validation", 100, 130, paint);
            }
        });
    }
}
