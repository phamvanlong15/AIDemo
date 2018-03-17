package com.phamvanlong.aidemo;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FaceRectangle;
import com.microsoft.projectoxford.face.contract.IdentifyResult;
import com.microsoft.projectoxford.face.contract.Person;
import com.microsoft.projectoxford.face.contract.TrainingStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private FaceServiceClient faceServiceClient = new FaceServiceRestClient("https://westcentralus.api.cognitive.microsoft.com/face/v1.0","daaf4d5323ed4068bcf3135f4031dee4");
    private final String personGroupId = "hollywoodstar";

    ImageView imageView;
    Bitmap mBitmap;
    Face[] facesDetected;

    class detectTask extends  AsyncTask<InputStream,String,Face[]> {
        private ProgressDialog mDialog = new ProgressDialog(MainActivity.this);


        @Override
        protected Face[] doInBackground(InputStream... params) {
            try{
                publishProgress("Detecting...");
                Face[] results = faceServiceClient.detect(params[0],true,false,null);
                if(results == null)
                {
                    publishProgress("Detection Finished. Nothing detected");
                    return null;
                }
                else{
                    publishProgress(String.format("Detection Finished. %d face(s) detected",results.length));
                    return results;
                }
            }
            catch (Exception ex)
            {
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            mDialog.show();
        }

        @Override
        protected void onPostExecute(Face[] faces) {
            mDialog.dismiss();
            facesDetected = faces;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            mDialog.setMessage(values[0]);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBitmap = BitmapFactory.decodeResource(getResources(),R.drawable.tom);
        imageView = (ImageView)findViewById(R.id.imgView);
        imageView.setImageBitmap(mBitmap);
        Button btnDetect = (Button)findViewById(R.id.btnDetectFace);
        Button btnIdentify = (Button)findViewById(R.id.btnIdentify);

        btnDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                mBitmap.compress(Bitmap.CompressFormat.JPEG,100,outputStream);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

                new detectTask().execute(inputStream);

            }
        });

        btnIdentify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final UUID[] faceIds = new UUID[facesDetected.length];
                for(int i=1;i<=facesDetected.length;i++){//i=0
                    faceIds[i] = facesDetected[i].faceId;
                }

                new IdentificationTask(personGroupId).execute(faceIds);
            }
        });


    }

    public class IdentificationTask extends AsyncTask<UUID,String,IdentifyResult[]> {
        String personGroupId = "hollywoodstar";

        private ProgressDialog mDialog = new ProgressDialog(MainActivity.this);

        public IdentificationTask(String personGroupId) {
            this.personGroupId = personGroupId;
        }

        @Override
        protected IdentifyResult[] doInBackground(UUID... params) {

            try{
                publishProgress("Getting person group status...");
                TrainingStatus trainingStatus  = faceServiceClient.getPersonGroupTrainingStatus(this.personGroupId);
                if(trainingStatus.status != TrainingStatus.Status.Succeeded)
                {
                    publishProgress("Person group training status is "+trainingStatus.status);
                    return null;
                }
                publishProgress("Identifying...");

                IdentifyResult[] results = faceServiceClient.identity(personGroupId, // person group id
                        params // face ids
                        ,1); // max number of candidates returned

                return results;

            } catch (Exception e)
            {
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            mDialog.show();
        }

        @Override
        protected void onPostExecute(IdentifyResult[] identifyResults) {
            mDialog.dismiss();

            for(IdentifyResult identifyResult:identifyResults)
            {
                new PersonDetectionTask(personGroupId).execute(identifyResult.candidates.get(0).personId);
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            mDialog.setMessage(values[0]);
        }
    }

    private class PersonDetectionTask extends AsyncTask<UUID,String,Person> {
        private ProgressDialog mDialog = new ProgressDialog(MainActivity.this);
        private String personGroupId = "hollywoodstar";

        public PersonDetectionTask(String personGroupId) {
            this.personGroupId = personGroupId;
        }

        @Override
        protected Person doInBackground(UUID... params) {
            try{
                publishProgress("Getting person group status...");

                return faceServiceClient.getPerson(personGroupId,params[0]);
            } catch (Exception e)
            {
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            mDialog.show();
        }

        @Override
        protected void onPostExecute(Person person) {
            mDialog.dismiss();

            ImageView img = (ImageView)findViewById(R.id.imgView);
            imageView.setImageBitmap(drawFaceRectangleOnBitmap(mBitmap,facesDetected,person.name));
        }

        @Override
        protected void onProgressUpdate(String... values) {
            mDialog.setMessage(values[0]);
        }
    }

    private Bitmap drawFaceRectangleOnBitmap(Bitmap mBitmap, Face[] facesDetected, String name) {

        Bitmap bitmap = mBitmap.copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas = new Canvas(bitmap);
        //Rectangle
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(12);

        if(facesDetected != null)
        {
            for(Face face:facesDetected)
            {
                FaceRectangle faceRectangle = face.faceRectangle;
                canvas.drawRect(faceRectangle.left,
                        faceRectangle.top,
                        faceRectangle.left+faceRectangle.width,
                        faceRectangle.top+faceRectangle.height,
                        paint);
                drawTextOnCanvas(canvas,100,((faceRectangle.left+faceRectangle.width)/2)+100,(faceRectangle.top+faceRectangle.height)+50,Color.WHITE,name);

            }
        }
        return bitmap;
    }

    private void drawTextOnCanvas(Canvas canvas, int textSize, int x, int y, int color, String name) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(textSize);

        float textWidth = paint.measureText(name);

        canvas.drawText(name,x-(textWidth/2),y-(textSize/2),paint);
    }
}
