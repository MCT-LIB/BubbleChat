package com.mct.bubblechat.test;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;

import com.mct.bubblechat.BubblesManager;
import com.permissionx.guolindev.PermissionX;
import com.permissionx.guolindev.callback.RequestCallback;

public class MainActivity extends AppCompatActivity {

    private boolean isInCutout = false;
    private boolean isStatusBarVisible;
    private boolean isNavigationBarVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        findViewById(R.id.btn_show_bubble).setOnClickListener(v -> {
            requestOverlayPermission(this, (allGranted, gl, dl) -> {
                if (allGranted) {
                    String key = ChatHeadService.EXTRA_CUTOUT_SAFE_AREA;
                    final Intent intent = new Intent(this, ChatHeadService.class);
                    intent.putExtra(key, BubblesManager.findCutoutSafeArea(this));
                    startService(intent);
                }
            });
        });
        findViewById(R.id.btn_next).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
        });
        findViewById(R.id.btn_toggle_cutout).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isInCutout = !isInCutout;
//                WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
//                if (isInCutout) {
//                    layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
//                } else {
//                    layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
//                }
//                getWindow().setAttributes(layoutParams);
                setSystemBarsVisible(isInCutout, WindowInsetsCompat.Type.systemBars());
            }
        });
        findViewById(R.id.btn_toggle_status_bar).setOnClickListener(v -> {
            isStatusBarVisible = !isStatusBarVisible;
            setSystemBarsVisible(isStatusBarVisible, WindowInsetsCompat.Type.statusBars());
        });
        findViewById(R.id.btn_toggle_navigation_bar).setOnClickListener(v -> {
            isNavigationBarVisible = !isNavigationBarVisible;
            setSystemBarsVisible(isNavigationBarVisible, WindowInsetsCompat.Type.navigationBars());
        });
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(layoutParams);
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setSystemBarsVisible(false, WindowInsetsCompat.Type.systemBars());
        /*/
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            int i = 0;
            @Override
            public void run() {
                Log.i("ddd", "Log: " + (++i));
                handler.postDelayed(this, 500);
            }
        });
        /*/

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void setSystemBarsVisible(boolean visible, int type) {
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (visible) {
            // Show the system bars.
            windowInsetsController.show(type);
        } else {
            // Hide the system bars.
            windowInsetsController.hide(type);
        }
    }

    static void requestOverlayPermission(FragmentActivity activity, RequestCallback callback) {
        PermissionX.init(activity)
                .permissions(Manifest.permission.SYSTEM_ALERT_WINDOW)
                .onExplainRequestReason((scope, deniedList) -> scope.showRequestReasonDialog(deniedList,
                        "You need to grant the app permission to use this feature.",
                        "OK",
                        "Cancel"))
                .request(callback);
    }
}