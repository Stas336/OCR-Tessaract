package com.stasl.ocrtest;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    static final int REQUEST_CODE_PHOTO = 1;
    private TessBaseAPI tessBaseApi;
    TextView textView;
    Button start, exit;
    Uri outputFileUri;
    File tessdata_directory;
    private static final String lang = "eng";
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AskForPermissions();
    }

    public void ActivityAfterGrantingPermissions()
    {
        textView = (TextView) findViewById(R.id.ExtractedText);
        start = (Button) findViewById(R.id.Start);
        exit = (Button) findViewById(R.id.Exit);
        if (start != null)
        {
            start.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    startCamera();
                }
            });
        }
    }

    private void startCamera()
    {
        try {
            tessdata_directory = new File(getApplicationContext().getFilesDir(), "tessdata");
            if (!tessdata_directory.exists())
            {
                tessdata_directory.mkdir();
            }
            outputFileUri = generatePhotoUri();
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            startActivityForResult(takePictureIntent, REQUEST_CODE_PHOTO);
        } catch (Exception e)
        {
            Snackbar.make(findViewById(android.R.id.content), e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    private Uri generatePhotoUri()
    {
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "OCRImgs");
        if (!directory.exists())
        {
            directory.mkdir();
        }
        File file = new File(directory.getPath() + "/" + "photo_" + System.currentTimeMillis() + ".jpg");
        return Uri.fromFile(file);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_CODE_PHOTO && resultCode == RESULT_OK)
        {
            copyDataFiles(tessdata_directory.getPath());
            new OCR().execute(outputFileUri);
        }
        else
        {
            Toast.makeText(this, "ERROR: Image was not obtained.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyDataFiles(String path)
    {
        try {
            String fileList[] = getAssets().list("tessdata");

            for (String fileName : fileList)
            {
                String pathToDataFile = path + "/" + fileName;
                File file = new File(pathToDataFile);
                if (!(file.exists()))
                {

                    InputStream in = getAssets().open("tessdata/" + fileName);

                    OutputStream out = new FileOutputStream(pathToDataFile);

                    byte[] buf = new byte[1024];
                    int len;

                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    in.close();
                    out.close();

                    Log.d(TAG, "Copied " + fileName + "to tessdata");
                }
            }
        } catch (IOException e)
        {
            Log.e(TAG, "Unable to copy files to tessdata " + e.toString());
        }
    }

    private void AskForPermissions()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
        else
        {
            ActivityAfterGrantingPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        switch (requestCode)
        {
            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE:
            {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
                else
                {
                    Snackbar.make(findViewById(android.R.id.content), "Permission has been denied to read external storage", Snackbar.LENGTH_LONG).setAction("RETRY", new View.OnClickListener() {
                        @Override
                        public void onClick(View view)
                        {
                            Intent mStartActivity = new Intent(getApplicationContext(), MainActivity.class);
                            int mPendingIntentId = 123456;
                            PendingIntent mPendingIntent = PendingIntent.getActivity(getApplicationContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                            AlarmManager mgr = (AlarmManager)getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1, mPendingIntent);
                            Runtime.getRuntime().exit(0);
                        }
                    }).setActionTextColor(Color.RED).show();
                }
            }
        }
    }

    public class OCR extends AsyncTask<Uri, Void, Void> {

        private String text = "";
        private Bitmap bitmap;

        @Override
        protected Void doInBackground(Uri... uris) {
            bitmap = modifyImage(uris[0]);
            text = extractText(bitmap);
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            Snackbar.make(findViewById(android.R.id.content), "Recognizing text is in progress...", Snackbar.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(Void aVoid)
        {
            super.onPostExecute(aVoid);
            Snackbar.make(findViewById(android.R.id.content), "Text was recognized...", Snackbar.LENGTH_SHORT).show();
            textView.setText(text);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Snackbar.make(findViewById(android.R.id.content), "Starting to recognize text...", Snackbar.LENGTH_SHORT).show();
        }

        private String extractText(Bitmap bitmap)
        {
            try
            {
                tessBaseApi = new TessBaseAPI();
            } catch (Exception e)
            {
                Snackbar.make(findViewById(android.R.id.content), e.getMessage(), Snackbar.LENGTH_SHORT).show();
                if (tessBaseApi == null)
                {

                    Log.e(TAG, "TessBaseAPI is null. TessFactory not returning tess object.");
                }
            }
            tessBaseApi.init(getApplicationContext().getFilesDir().getPath(), lang);

//       //EXTRA SETTINGS
//        //For example if we only want to detect numbers
//        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890");
//
//        //blackList Example
//        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-qwertyuiop[]}{POIU" +
//                "YTRWQasdASDfghFGHjklJKLl;L:'\"\\|~`xcvXCVbnmBNM,./<>?");

            tessBaseApi.setImage(bitmap);
            String extractedText = "";
            try
            {
                extractedText = tessBaseApi.getUTF8Text();
            } catch (Exception e)
            {
                Log.e(TAG, "Error in recognizing text.");
            }
            tessBaseApi.end();
            return extractedText;
        }

        private Bitmap modifyImage(Uri imgUri)
        {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4; // 1 - means max size. 4 - means maxsize/4 size. Don't use value <4, because you need more memory in the heap to store your data.
            bitmap = toGrayscale(bitmap);
            return BitmapFactory.decodeFile(imgUri.getPath(), options);
        }

        private Bitmap toGrayscale(Bitmap originalBMP)
        {
            int width, height;
            height = originalBMP.getHeight();
            width = originalBMP.getWidth();

            Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(bmpGrayscale);
            Paint paint = new Paint();
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0);
            ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
            paint.setColorFilter(f);
            c.drawBitmap(originalBMP, 0, 0, paint);
            return bmpGrayscale;
        }
    }
}