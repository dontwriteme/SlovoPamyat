package org.biblememory.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import static org.biblememory.app.ScreenUi.*;

final class ScreenArtwork {
    private final Context context;
    private final ScreenUi ui;
    ScreenArtwork(Context context,ScreenUi ui){this.context=context;this.ui=ui;}

    /** Six original bundled landscapes; no runtime network access or per-verse downloads. */
    int landscapeFor(long key){
        switch((int)Math.floorMod(key,6L)){
            case 0:return R.drawable.hero_sunrise;
            case 1:return R.drawable.hero_lake;
            case 2:return R.drawable.hero_autumn;
            case 3:return R.drawable.hero_forest;
            case 4:return R.drawable.hero_evening;
            default:return R.drawable.hero_valley;
        }
    }
    /** Warm pine-toned card surface, visually coherent with the new landscape artwork. */
    GradientDrawable homeSurface(){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFFFFCF8,0xFFFFF5EF,0xFFFBE9DD});
        d.setCornerRadius(ui.dp(22));d.setStroke(ui.dp(1),HOME_BORDER);return d;
    }
    static final int MAX_LANDSCAPE_THUMBNAIL_DP=96;
    // The six bundled landscape resources are shared only by thumbnails in this Activity.
    private final android.util.SparseArray<Bitmap> landscapeThumbnails=new android.util.SparseArray<>(6);

    /** A small decorative scenic preview; accessible names remain the collection labels. */
    ImageView landscapeThumbnail(long index){
        ImageView thumb=new ImageView(context);
        int resource=landscapeFor(index);
        Bitmap bitmap=landscapeThumbnails.get(resource);
        if(bitmap==null){
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inScaled=false;
            options.inJustDecodeBounds=true;
            BitmapFactory.decodeResource(context.getResources(),resource,options);
            int side=Math.min(options.outWidth,options.outHeight);
            int minimum=ui.dp(MAX_LANDSCAPE_THUMBNAIL_DP);
            options.inSampleSize=1;
            // CENTER_CROP still receives enough source pixels for the largest thumbnail.
            while(side/(options.inSampleSize*2)>=minimum)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;
            bitmap=BitmapFactory.decodeResource(context.getResources(),resource,options);
            if(bitmap!=null)landscapeThumbnails.put(resource,bitmap);
        }
        if(bitmap!=null)thumb.setImageBitmap(bitmap);
        else thumb.setImageResource(resource);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(ui.shape(CARD_ALT,14));
        thumb.setClipToOutline(true);
        thumb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return thumb;
    }
}
