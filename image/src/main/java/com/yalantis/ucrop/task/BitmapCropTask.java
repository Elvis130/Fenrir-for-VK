package com.yalantis.ucrop.task;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.yalantis.ucrop.callback.BitmapCropCallback;
import com.yalantis.ucrop.io.AndroidSchedulers;
import com.yalantis.ucrop.model.CropParameters;
import com.yalantis.ucrop.model.ImageState;
import com.yalantis.ucrop.util.BitmapLoadUtils;
import com.yalantis.ucrop.util.FileUtils;
import com.yalantis.ucrop.util.ImageHeaderParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableTransformer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Crops part of image that fills the crop bounds.
 * <p/>
 * First image is downscaled if max size was set and if resulting image is larger that max size.
 * Then image is rotated accordingly.
 * Finally new Bitmap object is created and saved to file.
 */
public class BitmapCropTask {

    private static final String TAG = "BitmapCropTask";

    private final WeakReference<Context> mContext;
    private final RectF mCropRect;
    private final RectF mCurrentImageRect;
    private final float mCurrentAngle;
    private final int mMaxResultImageSizeX, mMaxResultImageSizeY;
    private final Bitmap.CompressFormat mCompressFormat;
    private final int mCompressQuality;
    private final String mImageInputPath, mImageOutputPath;
    private final BitmapCropCallback mCropCallback;
    private Bitmap mViewBitmap;
    private float mCurrentScale;
    private int mCroppedImageWidth, mCroppedImageHeight;
    private int cropOffsetX, cropOffsetY;

    public BitmapCropTask(@NonNull Context context, @Nullable Bitmap viewBitmap, @NonNull ImageState imageState, @NonNull CropParameters cropParameters,
                          @Nullable BitmapCropCallback cropCallback) {

        mContext = new WeakReference<>(context);

        mViewBitmap = viewBitmap;
        mCropRect = imageState.getCropRect();
        mCurrentImageRect = imageState.getCurrentImageRect();

        mCurrentScale = imageState.getCurrentScale();
        mCurrentAngle = imageState.getCurrentAngle();
        mMaxResultImageSizeX = cropParameters.getMaxResultImageSizeX();
        mMaxResultImageSizeY = cropParameters.getMaxResultImageSizeY();

        mCompressFormat = cropParameters.getCompressFormat();
        mCompressQuality = cropParameters.getCompressQuality();

        mImageInputPath = cropParameters.getImageInputPath();
        mImageOutputPath = cropParameters.getImageOutputPath();

        mCropCallback = cropCallback;
    }

    private static CompletableTransformer applyCompletableIOToMainSchedulers() {
        return completable -> completable.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public void execute() {
        doInBackground().compose(applyCompletableIOToMainSchedulers())
                .subscribe(() -> {
                    Uri uri = Uri.fromFile(new File(mImageOutputPath));
                    mCropCallback.onBitmapCropped(uri, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight);
                }, mCropCallback::onCropFailure);
    }

    private Completable doInBackground() {
        if (mViewBitmap == null) {
            return Completable.error(new NullPointerException("ViewBitmap is null"));
        } else if (mViewBitmap.isRecycled()) {
            return Completable.error(new NullPointerException("ViewBitmap is recycled"));
        } else if (mCurrentImageRect.isEmpty()) {
            return Completable.error(new NullPointerException("CurrentImageRect is empty"));
        }

        try {
            crop();
            mViewBitmap = null;
        } catch (Throwable throwable) {
            return Completable.error(throwable);
        }

        return Completable.complete();
    }

    private boolean crop() throws IOException {
        // Downsize if needed
        if (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0) {
            float cropWidth = mCropRect.width() / mCurrentScale;
            float cropHeight = mCropRect.height() / mCurrentScale;

            if (cropWidth > mMaxResultImageSizeX || cropHeight > mMaxResultImageSizeY) {

                float scaleX = mMaxResultImageSizeX / cropWidth;
                float scaleY = mMaxResultImageSizeY / cropHeight;
                float resizeScale = Math.min(scaleX, scaleY);

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(mViewBitmap,
                        Math.round(mViewBitmap.getWidth() * resizeScale),
                        Math.round(mViewBitmap.getHeight() * resizeScale), false);
                if (mViewBitmap != resizedBitmap) {
                    mViewBitmap.recycle();
                }
                mViewBitmap = resizedBitmap;

                mCurrentScale /= resizeScale;
            }
        }

        // Rotate if needed
        if (mCurrentAngle != 0) {
            Matrix tempMatrix = new Matrix();
            tempMatrix.setRotate(mCurrentAngle, (float) mViewBitmap.getWidth() / 2, (float) mViewBitmap.getHeight() / 2);

            Bitmap rotatedBitmap = Bitmap.createBitmap(mViewBitmap, 0, 0, mViewBitmap.getWidth(), mViewBitmap.getHeight(),
                    tempMatrix, true);
            if (mViewBitmap != rotatedBitmap) {
                mViewBitmap.recycle();
            }
            mViewBitmap = rotatedBitmap;
        }

        cropOffsetX = Math.round((mCropRect.left - mCurrentImageRect.left) / mCurrentScale);
        cropOffsetY = Math.round((mCropRect.top - mCurrentImageRect.top) / mCurrentScale);
        mCroppedImageWidth = Math.round(mCropRect.width() / mCurrentScale);
        mCroppedImageHeight = Math.round(mCropRect.height() / mCurrentScale);

        boolean shouldCrop = shouldCrop(mCroppedImageWidth, mCroppedImageHeight);
        Log.i(TAG, "Should crop: " + shouldCrop);

        if (shouldCrop) {
            ExifInterface originalExif = new ExifInterface(mImageInputPath);
            saveImage(Bitmap.createBitmap(mViewBitmap, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight));
            if (mCompressFormat.equals(Bitmap.CompressFormat.JPEG)) {
                ImageHeaderParser.copyExif(originalExif, mCroppedImageWidth, mCroppedImageHeight, mImageOutputPath);
            }
            return true;
        } else {
            FileUtils.copyFile(mImageInputPath, mImageOutputPath);
            return false;
        }
    }

    private void saveImage(@NonNull Bitmap croppedBitmap) {
        Context context = mContext.get();
        if (context == null) {
            return;
        }

        OutputStream outputStream = null;
        ByteArrayOutputStream outStream = null;
        try {
            outputStream = new FileOutputStream(mImageOutputPath, false);
            outStream = new ByteArrayOutputStream();
            croppedBitmap.compress(mCompressFormat, mCompressQuality, outStream);
            outputStream.write(outStream.toByteArray());
            croppedBitmap.recycle();
        } catch (IOException exc) {
            Log.e(TAG, exc.getLocalizedMessage());
        } finally {
            BitmapLoadUtils.close(outputStream);
            BitmapLoadUtils.close(outStream);
        }
    }

    /**
     * Check whether an image should be cropped at all or just file can be copied to the destination path.
     * For each 1000 pixels there is one pixel of error due to matrix calculations etc.
     *
     * @param width  - crop area width
     * @param height - crop area height
     * @return - true if image must be cropped, false - if original image fits requirements
     */
    private boolean shouldCrop(int width, int height) {
        int pixelError = 1;
        pixelError += Math.round(Math.max(width, height) / 1000f);
        return (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0)
                || Math.abs(mCropRect.left - mCurrentImageRect.left) > pixelError
                || Math.abs(mCropRect.top - mCurrentImageRect.top) > pixelError
                || Math.abs(mCropRect.bottom - mCurrentImageRect.bottom) > pixelError
                || Math.abs(mCropRect.right - mCurrentImageRect.right) > pixelError
                || mCurrentAngle != 0;
    }
}
