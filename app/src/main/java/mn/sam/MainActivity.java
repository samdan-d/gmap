package mn.sam;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

//Дараах даалгавруудыг хийж гүйцэтгэ.
//        1. Газрын зургыг бүх боломжит хэлбэрээр харуулах
//        2. Газрын зураг дээр дараах тэмдэглэгээнүүдийг хйи
//        а. Өөрийн байршил, байршил өөрчлөгдхөд шинчилнэ. Мөн өөрийн явсан замыг зурч үзүүлнэ.

//        b. газрын зураг дээр удаан дархад шинэ тэмдэглэгээ үүсгэнэ. Харин өмнө үүсгэсэн тэмдэглэгээ
//              нь дээр удаан дарах үед тухайн тэмдэглгээ устана. Жич: энэ тэмдэглэгээ нь өөрийн
//              байршилийг заах тэмдэглэгээнээс өөр байна.
//        c. сүүлд тэмдэглэсэн хоёр цэгийн хоорондох зайг олох
public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback,
        GoogleMap.OnMapLongClickListener,
        GoogleMap.OnMarkerDragListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    private static int counter = 0;

    private TextView tvDist;
    private SupportMapFragment mapFragment;
    private int mapType = 1;
    private GoogleMap googleMap;
    private Marker firstMarker;
    private Marker secondMarker;
    private Polyline line;

    // ex1
    private Location location;
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;
    private static final long UPDATE_INVERVAL = 5000;
    // lists of permissions
    private ArrayList<String> permissionsToRequest;
    private ArrayList<String> permissions = new ArrayList<>();

    private static final int ALL_PERMISSIONS_RESULT = 1011;
    private Polyline currentLine;
    private MarkerOptions previewMarker;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        tvDist = findViewById(R.id.tvDist);
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        registerForContextMenu((findViewById(R.id.button)));

        // permissions
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        permissionsToRequest = permissionsToRequest(permissions);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionsToRequest.size() > 0) {
                requestPermissions(permissionsToRequest.
                        toArray(new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
            }
        }

        // google api
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (googleApiClient != null)
            googleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (googleApiClient != null && googleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
            googleApiClient.disconnect();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // Add a marker in Sydney, Australia,
        // and move the map's camera to the same location.
        this.googleMap = googleMap;

        googleMap.setOnMapLongClickListener(this);
        googleMap.setOnMarkerDragListener(this);

        googleMap.setMapType(mapType);
    }

    @Override
    public void onMapLongClick(LatLng point) {
        Marker newMarker = googleMap.addMarker(new MarkerOptions()
                .position(point)
                .title("You are here")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        newMarker.setDraggable(true);

        if (counter % 2 == 0) {
            firstMarker = newMarker;
        } else {
            secondMarker = newMarker;
        }
        if (firstMarker != null && secondMarker != null)
            computeDistances();

        counter++;
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        if (firstMarker != null && firstMarker.equals(marker)) {
            firstMarker = null;
            if (line != null) line.remove();
            counter = 0;
        }
        if (secondMarker != null && secondMarker.equals(marker)) {
            secondMarker = null;
            if (line != null) line.remove();
            counter = 1;
        }

        marker.remove();
    }

    @Override
    public void onMarkerDrag(Marker marker) {

    }

    @Override
    public void onMarkerDragEnd(Marker marker) {

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.setHeaderTitle("Map types");
        menu.add(0, 0, 0, "Normal");
        menu.add(0, 1, 0, "Hybird");
        menu.add(0, 2, 0, "Satellite");
        menu.add(0, 3, 0, "Terrain");
        menu.add(0, 4, 0, "None");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                mapType = GoogleMap.MAP_TYPE_NORMAL;
                break;
            case 1:
                mapType = GoogleMap.MAP_TYPE_HYBRID;
                break;
            case 2:
                mapType = GoogleMap.MAP_TYPE_SATELLITE;
                break;
            case 3:
                mapType = GoogleMap.MAP_TYPE_TERRAIN;
                break;
            case 4:
                mapType = GoogleMap.MAP_TYPE_NONE;
        }
        mapFragment.getMapAsync(this);
        return true;
    }

    private void computeDistances() {
        if (line != null) line.remove();

        double distanceBetween = SphericalUtil.computeDistanceBetween(firstMarker.getPosition(), secondMarker.getPosition());
        line = googleMap.addPolyline(new PolylineOptions()
                .add(firstMarker.getPosition(), secondMarker.getPosition())
                .width(5)
                .color(Color.RED));

        String url = getUrl(firstMarker.getPosition(), secondMarker.getPosition(), "walking");
        tvDist.setText(Math.round((distanceBetween)) + "m");

        new FetchURL().execute(url);

    }

    private String getUrl(LatLng origin, LatLng dest, String directionMode) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=" + directionMode;
        String parameters = str_origin + "&" + str_dest + "&" + mode;
        String output = "json";
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + getString(R.string.google_maps_key);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        System.out.println("current location!!!!!!!!!!!!!1");

        if (location != null) {
            LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
            previewMarker = new MarkerOptions()
                    .position(currentPosition)
                    .title("Current")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.sm_locs));
            googleMap.addMarker(previewMarker);

            googleMap.moveCamera(CameraUpdateFactory.zoomTo(20.0f));
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(currentPosition));
        }

        startLocationUpdates();
    }

    private void startLocationUpdates() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INVERVAL);

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {

        if (location != null) {
            LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());

            currentLine = googleMap.addPolyline(new PolylineOptions()
                    .add(previewMarker.getPosition(), currentPosition)
                    .width(5)
                    .color(Color.BLACK));

            previewMarker = new MarkerOptions()
                    .position(currentPosition)
                    .title("Current")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.sm_locs));

            googleMap.addMarker(previewMarker);
        }
    }

    private class FetchURL extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            String data = "";
            try {
                downloadUrl(strings[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
        }

        private void downloadUrl(String strUrl) throws IOException {
            InputStream stream = null;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(strUrl).openConnection();
                conn.setRequestMethod("GET");
                stream = conn.getInputStream();

                BufferedReader rd = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
                StringBuilder sb = new StringBuilder();
                int cp;
                while ((cp = rd.read()) != -1) {
                    sb.append((char) cp);
                }

                System.out.println(sb);

            } catch (Exception e) {
                // In your production code handle any errors and catch the individual exceptions
                e.printStackTrace();
            } finally {
                stream.close();
            }
        }
    }

    private ArrayList<String> permissionsToRequest(ArrayList<String> wantedPermissions) {
        ArrayList<String> result = new ArrayList<>();

        for (String perm : wantedPermissions) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    private boolean hasPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }
}
