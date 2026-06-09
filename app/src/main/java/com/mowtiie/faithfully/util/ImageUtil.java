package com.mowtiie.faithfully.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtil {

    public static final int FULL_RES_WIDTH  = 1920;
    public static final int THUMB_WIDTH     = 400;
    public static final int FULL_RES_QUALITY = 85;
    public static final int THUMB_QUALITY    = 80;

    public static byte[] processImage(Context context, Uri uri, int maxWidth, int quality) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }

        int sampleSize = 1;
        while ((bounds.outWidth / sampleSize) > maxWidth * 2) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sampleSize;
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, decode);
        }
        if (bitmap == null) throw new IOException("Could not decode image");

        int rotation = readExifRotation(context, uri);

        if (bitmap.getWidth() > maxWidth) {
            float scale = (float) maxWidth / bitmap.getWidth();
            int newHeight = Math.round(bitmap.getHeight() * scale);
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true);
            if (scaled != bitmap) bitmap.recycle();
            bitmap = scaled;
        }

        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            bitmap = rotated;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    private static int readExifRotation(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;

            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }
}
