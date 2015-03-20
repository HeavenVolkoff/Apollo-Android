package com.inc.vasconcellos.apollo;
import android.graphics.drawable.Drawable;
import android.graphics.Shader;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.ColorFilter;
import android.graphics.Canvas;
public class logoDrawable extends Drawable {
private static final float[] VIEW_BOX = { 71.699997f, 1.100000f, 58.599998f, 64.800003f };
private RectF rect = new RectF();
private Matrix matrix = new Matrix();
private Shader shader;
private Path p = new Path();
private Paint paint = new Paint();
@Override
public void draw(Canvas canvas) {
paint.setAntiAlias(true);
float viewBoxWidth = VIEW_BOX[2];
float viewBoxHeight = VIEW_BOX[3];
Rect bounds = getBounds();
if (viewBoxHeight <= 0 || viewBoxWidth <= 0 || bounds.width() <= 0 || bounds.height() <= 0) {
return;
}
canvas.save();
float viewBoxRatio = viewBoxWidth / (float) viewBoxHeight;
float boundsRatio = bounds.width() / (float) bounds.height();
float factorScale;
if (boundsRatio > viewBoxRatio) {
 // canvas larger than viewbox
 factorScale = bounds.height() / (float) viewBoxHeight;
} else {
 // canvas higher (or equals) than viewbox
 factorScale = bounds.width() / (float) viewBoxWidth;
}
int newViewBoxHeight = Math.round(factorScale * viewBoxHeight);
int newViewBoxWidth = Math.round(factorScale * viewBoxWidth);
int marginX = bounds.width() - newViewBoxWidth;
int marginY = bounds.height() - newViewBoxHeight;
canvas.translate(bounds.left, bounds.top);
canvas.translate(Math.round(marginX / 2f), Math.round(marginY / 2f));
canvas.clipRect(0, 0, newViewBoxWidth, newViewBoxHeight);
canvas.translate(-Math.round(factorScale * VIEW_BOX[0]), -Math.round(factorScale * VIEW_BOX[1]));
paint.setAlpha(255);
paint.setAlpha(255);
paint.setAlpha(255);
paint.setAlpha(255);
p.reset();
p.moveTo(factorScale * 102.199997f, factorScale * 40.000000f);
p.rLineTo(0, factorScale * 16.600000f);
p.rLineTo(factorScale * 7.900000f, factorScale * -7.500000f);
p.rLineTo(0, factorScale * -7.500000f);
p.rCubicTo(factorScale * 0.300000f, factorScale * -5.800000f, factorScale * 3.600000f, factorScale * -9.100000f, factorScale * 10.000000f, factorScale * -10.000000f);
p.rLineTo(factorScale * 8.500000f, factorScale * 0.000000f);
p.rLineTo(factorScale * 1.800000f, factorScale * -1.700000f);
p.lineTo(factorScale * 112.199997f, factorScale * 30.000000f);
p.cubicTo(factorScale * 105.800003f, factorScale * 30.900000f, factorScale * 102.500000f, factorScale * 34.200001f, factorScale * 102.199997f, factorScale * 40.000000f);
p.close();
p.moveTo(factorScale * 102.199997f, factorScale * 40.000000f);
paint.setShader(null);
paint.setColor(-65794);
paint.setAlpha(255);
paint.setStyle(Paint.Style.FILL);
canvas.drawPath(p, paint);
paint.setAlpha(255);
p.reset();
p.moveTo(factorScale * 102.000000f, factorScale * 1.100000f);
p.rLineTo(0, factorScale * 16.700001f);
p.rCubicTo(factorScale * 0.300000f, factorScale * 5.800000f, factorScale * 3.600000f, factorScale * 9.100000f, factorScale * 10.000000f, factorScale * 10.000000f);
p.rLineTo(factorScale * 18.100000f, factorScale * 0.100000f);
p.rLineTo(factorScale * -7.400000f, factorScale * -7.000000f);
p.rLineTo(factorScale * -8.700000f, factorScale * 0.000000f);
p.rCubicTo(factorScale * -6.400000f, factorScale * -0.900000f, factorScale * -9.700000f, factorScale * -4.200000f, factorScale * -10.000000f, factorScale * -10.000000f);
p.lineTo(factorScale * 104.000008f, factorScale * 3.100000f);
p.lineTo(factorScale * 102.000000f, factorScale * 1.100000f);
p.close();

p.moveTo(factorScale * 102.000000f, factorScale * 1.100000f);
paint.setShader(null);
paint.setColor(-65794);
paint.setAlpha(255);
paint.setStyle(Paint.Style.FILL);
canvas.drawPath(p, paint);
paint.setAlpha(255);
p.reset();
p.moveTo(factorScale * 71.699997f, factorScale * 37.000000f);
p.rLineTo(factorScale * 1.800000f, factorScale * -1.700000f);
p.rLineTo(factorScale * 8.500000f, factorScale * 0.000000f);
p.rCubicTo(factorScale * 6.400000f, factorScale * -0.900000f, factorScale * 9.700000f, factorScale * -4.200000f, factorScale * 10.000000f, factorScale * -10.000000f);
p.rLineTo(0, factorScale * -7.500000f);
p.rLineTo(factorScale * 7.900000f, factorScale * -7.500000f);
p.lineTo(factorScale * 99.900002f, factorScale * 27.000000f);
p.rCubicTo(factorScale * -0.300000f, factorScale * 5.800000f, factorScale * -3.600000f, factorScale * 9.100000f, factorScale * -10.000000f, factorScale * 10.000000f);
p.lineTo(factorScale * 71.699997f, factorScale * 37.000000f);
p.close();
p.moveTo(factorScale * 71.699997f, factorScale * 37.000000f);
paint.setShader(null);
paint.setColor(-65794);
paint.setAlpha(255);
paint.setStyle(Paint.Style.FILL);
canvas.drawPath(p, paint);
paint.setAlpha(255);
p.reset();
p.moveTo(factorScale * 100.000000f, factorScale * 65.900002f);
p.lineTo(factorScale * 100.000000f, factorScale * 65.900002f);
p.lineTo(factorScale * 100.000000f, factorScale * 49.299999f);
p.rCubicTo(factorScale * -0.300000f, factorScale * -5.800000f, factorScale * -3.600000f, factorScale * -9.100000f, factorScale * -10.000000f, factorScale * -10.000000f);
p.rLineTo(factorScale * -18.100000f, factorScale * -0.100000f);
p.rLineTo(0, factorScale * 0.000000f);
p.rLineTo(factorScale * 7.300000f, factorScale * 7.000000f);
p.rLineTo(factorScale * 8.700000f, factorScale * 0.000000f);
p.rCubicTo(factorScale * 6.400000f, factorScale * 0.900000f, factorScale * 9.700000f, factorScale * 4.200000f, factorScale * 10.000000f, factorScale * 10.000000f);
p.rLineTo(0, factorScale * 7.700000f);
p.lineTo(factorScale * 100.000000f, factorScale * 65.900002f);
p.close();
p.moveTo(factorScale * 100.000000f, factorScale * 65.900002f);
paint.setShader(null);
paint.setColor(-65794);
paint.setAlpha(255);
paint.setStyle(Paint.Style.FILL);
canvas.drawPath(p, paint);
canvas.restore();
}
@Override public void setAlpha(int alpha) { }
@Override public void setColorFilter(ColorFilter cf) { }
@Override public int getOpacity() { return 0; }
@Override public int getMinimumHeight() { return 11; }
@Override public int getMinimumWidth() { return 10; }
}

