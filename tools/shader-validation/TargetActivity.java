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
        boolean hidden = getIntent().getBooleanExtra("hide_overlays", false);
        if (android.os.Build.VERSION.SDK_INT >= 31) getWindow().setHideOverlayWindows(hidden);
        setContentView(new View(this) {
            final Paint paint = new Paint();
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(Color.rgb(220, 220, 220));
                paint.setColor(Color.rgb(40, 150, 230));
                canvas.drawRect(100, 200, 700, 800, paint);
                paint.setColor(Color.BLACK); paint.setTextSize(44);
                canvas.drawText(hidden ? "OVERLAYS BLOCKED" : "OVERLAYS ALLOWED", 100, 130, paint);
            }
        });
    }
}
