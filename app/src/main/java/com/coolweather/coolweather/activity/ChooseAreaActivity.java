package com.coolweather.coolweather.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.coolweather.R;
import com.coolweather.coolweather.db.CoolWeatherDB;
import com.coolweather.coolweather.model.City;
import com.coolweather.coolweather.model.Country;
import com.coolweather.coolweather.model.Province;
import com.coolweather.coolweather.util.HttpCallbackListener;
import com.coolweather.coolweather.util.HttpUtil;
import com.coolweather.coolweather.util.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ZongJie on 2016/4/20.
 */
public class ChooseAreaActivity extends Activity {
    private TextView titleText;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList=new ArrayList<String>();
    private CoolWeatherDB coolWeatherDB;

    private List<Province> provinceList;//省列表
    private List<City> cityList;//市列表
    private List<Country> countryList;//县列表

    private Province selectedProvince;
    private City selectedCity;
    private ProgressDialog progressDialog;

    private int currentLevel;//当前选中的级别
    public static final int LEVEL_PROVINCE=0;
    public static final int LEVEL_CITY=1;
    public static final int LEVEL_COUNTRY=2;

    private boolean isFromWeatherActivity;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFromWeatherActivity=getIntent().getBooleanExtra("from_weather_activity",false);
        SharedPreferences pref= PreferenceManager.getDefaultSharedPreferences(ChooseAreaActivity.this);
        if(pref.getBoolean("city_selected",false)&&!isFromWeatherActivity){
            Intent intent=new Intent(ChooseAreaActivity.this,WeatherActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.choose_area);
        titleText= (TextView) findViewById(R.id.title_text);
        listView= (ListView) findViewById(R.id.list_view);
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        coolWeatherDB=CoolWeatherDB.getInstance(this);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
                if(currentLevel==LEVEL_PROVINCE){
                    selectedProvince=provinceList.get(index);
                    queryCities();
                }else if(currentLevel==LEVEL_CITY){
                    selectedCity=cityList.get(index);
                    queryCountries();
                }else if(currentLevel==LEVEL_COUNTRY){
                    String countryCode=countryList.get(index).getCountryCode();
                    Intent intent=new Intent(ChooseAreaActivity.this,WeatherActivity.class);
                    intent.putExtra("country_code",countryCode);
                    startActivity(intent);
                    finish();
                }

            }
        });
        queryProvinces();
    }
    //查询全国所有的省，优先从数据库查询，如果没有再去服务器上查询。
    private void queryProvinces(){
        provinceList=coolWeatherDB.loadProvinces();
        if(provinceList.size()>0){
            dataList.clear();
            //for(声明循环变量 : 数组名):遍历数组
            for(Province province:provinceList){
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();//notifyDataSetChanged():在修改适配器绑定的数组后，不用重新刷新Activity，通知Activity更新ListView
            listView.setSelection(0);//ListView.setSelection(position)，表示将列表移动到指定的Position处。
            titleText.setText("中国");
            currentLevel=LEVEL_PROVINCE;
        }else{
            queryFromServer(null,"province");
        }
    }
    //查询选中省内所有的市，优先从数据库查询，如果没有再去服务器上查询。
    private void queryCities(){
        cityList=coolWeatherDB.loadCities(selectedProvince.getId());
        if(cityList.size()>0){
            dataList.clear();
            for(City city:cityList){
                dataList.add(city.getCityName());
                adapter.notifyDataSetChanged();
                listView.setSelection(0);
                titleText.setText(selectedProvince.getProvinceName());
                currentLevel=LEVEL_CITY;
            }
        }else{
            queryFromServer(selectedProvince.getProvinceCode(),"city");
        }
    }
    //查询选中市内所有的县，优先从数据库查询，如果没有再去服务器上查询。
    private void queryCountries(){
        countryList=coolWeatherDB.loadCountries(selectedCity.getId());
        if(countryList.size()>0){
            dataList.clear();
            for(Country country:countryList){
                dataList.add(country.getCountryName());
                adapter.notifyDataSetChanged();
                listView.setSelection(0);
                titleText.setText(selectedCity.getCityName());
                currentLevel=LEVEL_COUNTRY;
            }
        }else{
            queryFromServer(selectedCity.getCityCode(),"country");
        }
    }
    //根据传入的代号和类型从服务器上查询省市县数据
    private void queryFromServer(final String code,final String type){
        String address;
        if(!TextUtils.isEmpty(code)){
            address="http://www.weather.com.cn/data/list3/city"+code+".xml";
        }else{
            address="http://www.weather.com.cn/data/list3/city.xml";
        }
        //显示进度对话框
        showProgressDialog();
        HttpUtil.sendHttpRequestWithHttpURLConnection(address, new HttpCallbackListener() {
            @Override
            public void onFinish(String response) {
                boolean result=false;
                if("province".equals(type)){
                    result= Utility.handleProvincesResponse(coolWeatherDB,response);
                }else if("city".equals(type)){
                    result=Utility.handleCitiesResponse(coolWeatherDB,response,selectedProvince.getId());
                }else if("country".equals(type)){
                    result=Utility.handleCountriesResponse(coolWeatherDB,response,selectedCity.getId());
                }
                if(result){
                    //通过runOnUiThread()回到主线程处理逻辑
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //关闭进度对话框
                            closeProgressDialog();
                            if("province".equals(type)){
                                queryProvinces();
                            }else if("city".equals(type)){
                                queryCities();
                            }else if("country".equals(type)){
                                queryCountries();
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //关闭进度对话框
                        closeProgressDialog();
                        Toast.makeText(ChooseAreaActivity.this,"加载失败",Toast.LENGTH_SHORT).show();
                    }
                });

            }
        });

    }
    private void showProgressDialog(){
        if(progressDialog==null){
            progressDialog=new ProgressDialog(this);
            progressDialog.setMessage("正在加载......");
            progressDialog.setCanceledOnTouchOutside(false);//就是在loading的时候，如果你触摸屏幕其它区域，就会让这个progressDialog消失，然后可能出现崩溃问题
        }
        progressDialog.show();
    }
    private void closeProgressDialog(){
        if(progressDialog!=null){
            progressDialog.dismiss();
        }
    }
    //捕获Back按键，根据currentLevel判断此时应该返回哪一列表，还是直接退出
    @Override
    public void onBackPressed() {
        if(currentLevel==LEVEL_COUNTRY){
            queryCities();
        }else if(currentLevel==LEVEL_CITY){
            queryProvinces();
        }else{
            if(isFromWeatherActivity){
                Intent intent=new Intent(this,WeatherActivity.class);
                startActivity(intent);
            }
            finish();
        }
    }
}
