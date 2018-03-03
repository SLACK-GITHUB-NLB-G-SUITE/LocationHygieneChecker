package com.example.jokubas.restauranthygienechecker;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.jokubas.restauranthygienechecker.util.SearchQueries;
import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;

import java.util.ArrayList;
import java.util.List;

import cz.msebera.android.httpclient.Header;

/**
 * The type Advanced search activity.
 */
public class AdvancedSearchActivity extends AppCompatActivity {


    private List<String> businessTypesSpinner = new ArrayList<>();
    private List<String> regionsSpinner = new ArrayList<>();
    private List<String> authoritiesSpinner = new ArrayList<>();
    private List<RegionsWrapper.Regions> regionsStorage;
    private List<BusinessTypes.businessTypes> businessTypesStorage;
    private List<AuthoritiesWrapper.Authorities> authoritiesStorage;
    private EditText businessNameView;
    private Spinner businessTypeView;
    private Spinner ratingLowView;
    private Spinner ratingHighView;
    private CheckBox checkBox;
    private Spinner radiusView;
    private Spinner regionView;
    private Spinner authoritiesView;
    private Button search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.advanced_search);

        // initialise all the views for later usage
        businessNameView = findViewById(R.id.business_name);
        businessTypeView = findViewById(R.id.type_of_business);
//        ratingLowView = findViewById(R.id.rating_low);
        ratingHighView = findViewById(R.id.rating_high);
        checkBox = findViewById(R.id.current_loc_check_box);
        radiusView = findViewById(R.id.radius_spinner);
        regionView = findViewById(R.id.region_spinner);
        authoritiesView = findViewById(R.id.authority_spinner);
        search = findViewById(R.id.search);

        // query the main components needed for the initial view
        try {
            readUrl(SearchQueries.BUSINESS_TYPES_URL, QueryType.BUSINESS_TYPE);
            readUrl(SearchQueries.REGIONS_URL, QueryType.REGIONS);
            readUrl(SearchQueries.AUTHORITIES_URL, QueryType.AUTHORITIES);
        } catch (Exception ex) {
            Log.e("READ_URL", ex.getMessage());
        }

        // when item from region view is selected, the authorities items have
        // to be displayed belonging to the particular region
        regionView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                addAuthorities();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        // depending on whether checkBox is checked or not
        // we enable or disable appropriate views
        manageCheckBox();

    }


    /**
     * On click search.
     *
     * @param view the view
     */
    public void onClickSearch(View view) {

        // TODO check if the data was fetched from the internet

        // Query data to be passed between different activities
        QueryData dataToPass = new QueryData();

        // intent from the current activity back to the main activity
        Intent intent = new Intent(AdvancedSearchActivity.this, MainActivity.class);

        // set the business name to the data to be passed
        dataToPass.name = businessNameView.getText().toString();

        // set business type id, -1 indicates failure
        dataToPass.businessTypeId = -1;
        String type = businessTypesSpinner.get(businessTypeView.getSelectedItemPosition());
        for (BusinessTypes.businessTypes b : businessTypesStorage)
            dataToPass.businessTypeId = b.BusinessTypeName.equals(type) ?
                    b.BusinessTypeId : dataToPass.businessTypeId;

        // set rating value
        dataToPass.ratingKey = ratingHighView.getSelectedItem().toString();

        // set the maximum distance from the users location
        dataToPass.maxDistanceLimit = Integer.valueOf(radiusView.getSelectedItem().toString());

        // authority id, -1 indicates failure
        dataToPass.localAuthorityId = -1;

        // if the checkBox is checked, it means that user is going to use
        // ones own location for finding the establishments
        if (checkBox.isChecked()) {

            // TODO CHECK WHETHER LOCATION ACTUALLY EXISTS AND IS ENABLED
            // check whether the GPS status is actually enabled and we can use it
            // if it is not the case display error message and abort
            if (!checkGpsStatus()) {
                noResultsToast(R.string.not_enabled);
                return;
            }

            // otherwise set the flag to indicate that the current location
            // is going to be used for querying the API endpoint
            dataToPass.useLocation = true;

        }
        // if checkBox is not checked, it means that user is going to use
        // region and local authority from the spinners
        else {
            // TODO exception when no input
            // check whether authority was selected, if not display
            // the error message and abort further actions
            int position = authoritiesView.getSelectedItemPosition();
            if(position < 0){
                noResultsToast(R.string.no_authority);
                return;
            }

            // if authority is selected then fetch the details about it
            // and add it to the data to be passed back to the main activity
            String authName = authoritiesSpinner.get(position);
            for (AuthoritiesWrapper.Authorities a : authoritiesStorage) {
                if (a.Name.equals(authName)) {
                    dataToPass.localAuthorityId = a.LocalAuthorityId;
                    break;
                }
            }

            // set the flag to indicate that the authority and region
            // are going to be used for querying the API endpoint
            dataToPass.useLocation = false;

        }

        // put the data to be passed into the intent and start the activity
        intent.putExtra(SearchQueries.QUERY_DATA, dataToPass);
        startActivity(intent);
        finish();

    }

    /**
     * On click check box.
     *
     * @param view the view
     */
    public void onClickCheckBox(View view) {
        manageCheckBox();
    }

    private void manageCheckBox() {
        if (checkBox.isChecked()) {
            // TODO if current location is used make sure that user gave access to the GPS
            radiusView.setEnabled(true);
            regionView.setEnabled(false);
            authoritiesView.setEnabled(false);
        } else {
            radiusView.setEnabled(false);
            regionView.setEnabled(true);
            authoritiesView.setEnabled(true);
        }
    }

    private void readUrl(String urlString, final QueryType type) throws Exception {

        AsyncHttpClient client = new AsyncHttpClient();

        // setting the headers for Food Hygiene API
        client.addHeader("x-api-version", "2");
        client.addHeader("Accept", "application/json");
        client.get(urlString, new TextHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, String response) {
                Gson gson = new Gson();
                switch (type) {
                    case BUSINESS_TYPE:
                        BusinessTypes result = gson.fromJson(response, BusinessTypes.class);
                        populateBusinessTypes(result);
                        break;
                    case REGIONS:
                        RegionsWrapper regions = gson.fromJson(response, RegionsWrapper.class);
                        populateRegions(regions);
                        break;
                    case AUTHORITIES:
                        AuthoritiesWrapper auth = gson.fromJson(response, AuthoritiesWrapper.class);
                        populateAuthorities(auth);
                        break;
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String response, Throwable t) {
                // TODO figure out later
            }
        });
    }

    private void populateBusinessTypes(BusinessTypes types) {
        businessTypesStorage = types.businessTypes;
        for (BusinessTypes.businessTypes type : businessTypesStorage) {
            businessTypesSpinner.add(type.BusinessTypeName);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, businessTypesSpinner);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        businessTypeView.setAdapter(adapter);
    }

    private void populateRegions(RegionsWrapper regions) {
        regionsStorage = regions.regions;
        for (RegionsWrapper.Regions r : regionsStorage) {
            regionsSpinner.add(r.name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, regionsSpinner);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        regionView.setAdapter(adapter);
    }

    private void populateAuthorities(AuthoritiesWrapper auth) {
        authoritiesStorage = auth.authorities;
        for (AuthoritiesWrapper.Authorities a : authoritiesStorage) {
            authoritiesSpinner.add(a.Name);
        }
    }

    private void addAuthorities() {
        if (authoritiesStorage == null) return;
        String rName = regionsSpinner.get(regionView.getSelectedItemPosition());
        authoritiesSpinner.clear();
        for (AuthoritiesWrapper.Authorities a : authoritiesStorage) {
            if (a.RegionName.equals(rName))
                authoritiesSpinner.add(a.Name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, authoritiesSpinner);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        authoritiesView.setAdapter(adapter);
    }


    private boolean checkGpsStatus() {
        LocationManager locationManager = (LocationManager) getApplicationContext().
                getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void noResultsToast(int errorMessage) {
        Toast toast = new Toast(getBaseContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        LayoutInflater inflater = (LayoutInflater) getBaseContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.toast, null);
        ((TextView) view.findViewById(R.id.error_message)).setText(errorMessage);
        toast.setView(view);
        toast.show();
    }

}
