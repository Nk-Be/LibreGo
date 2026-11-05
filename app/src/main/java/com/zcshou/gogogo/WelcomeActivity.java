package com.zcshou.gogogo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.zcshou.utils.GoUtils;

import java.util.ArrayList;

public class WelcomeActivity extends AppCompatActivity {
    private static final int SDK_PERMISSION_REQUEST = 127;
    private static final ArrayList<String> ReqPermissions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 生成默认参数的值
        PreferenceManager.setDefaultValues(this, R.xml.preferences_main, false);

        setContentView(R.layout.activity_welcome);

        Button startBtn = findViewById(R.id.startButton);
        startBtn.setOnClickListener(v -> handleStart());

        // 检查权限，若已授权可直接进入
        if (hasRequiredPermissions()) {
            startMainActivity();
        } else {
            checkDefaultPermissions();
        }
    }

    private boolean hasRequiredPermissions() {
        boolean hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return hasFine || hasCoarse;
    }

    private void handleStart() {
        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_gps));
            return;
        }

        if (hasRequiredPermissions()) {
            startMainActivity();
        } else {
            checkDefaultPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == SDK_PERMISSION_REQUEST) {
            if (hasRequiredPermissions()) {
                startMainActivity();
            } else {
                GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_permission));
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void checkDefaultPermissions() {
        ReqPermissions.clear();

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ReqPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ReqPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ReqPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!ReqPermissions.isEmpty()) {
            requestPermissions(ReqPermissions.toArray(new String[0]), SDK_PERMISSION_REQUEST);
        }
    }

    private void startMainActivity() {
        Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
