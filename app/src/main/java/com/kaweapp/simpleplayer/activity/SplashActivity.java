package com.kaweapp.simpleplayer.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.widget.Toast;

import com.kaweapp.simpleplayer.java.Constants;
import com.kaweapp.simpleplayer.service.MusicService;

import java.util.ArrayList;
import java.util.List;

public class SplashActivity extends AppCompatActivity {

    public static final int STORAGE_CODE  = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermissionIfNeccessary(Constants.PERMISSION_STORAGE, STORAGE_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            if(requestCode == STORAGE_CODE) {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            }
        }
        else {
            finish();
        }
    }

    public void requestPermissionIfNeccessary(String []requiredPermissions,int requestCode){
        List<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if(missingPermissions.isEmpty()){
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
        }
        else{
            requestPermission(missingPermissions,requestCode);
        }

    }

    private void requestPermission(List<String>missingPermissions,int requestCode){
        if (needPermissionsRationale(missingPermissions)) {
            Toast.makeText(this, "", Toast.LENGTH_LONG).show();
        }

        ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[missingPermissions.size()]), requestCode);
    }


    private boolean needPermissionsRationale(List<String> permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }

        return false;
    }


}
