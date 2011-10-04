package com.adamcarruthers.foundry;

import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.adamcarruthers.foundry.widget.PagerHeader;

public class APTActivity extends FragmentActivity {
	private ImageButton mShare;
	private ImageButton mSearch;
    private ViewPager mPager;
    private Context mContext;
    private PagerAdapter mPagerAdapter;
    private boolean mRooted;
    private SharedPreferences mPreferences;
	private int mSubsystemVer;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mContext = getApplicationContext();
        
        // TODO: According to testing, reads and writes are being made on the UI thread. Hunt them down...
        /*StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
        	.detectNetwork()
        	.detectDiskReads()
        	.detectDiskWrites()
        	.penaltyLog()
        	.penaltyDialog()
        	.build());*/
        
       /* note that we will run the root check on every launch
    	* as users can root their device without the app being uninstalled
    	* Do not run the root check if we already know it is rooted
    	*/
    	mPreferences = mContext.getSharedPreferences(Constants.SHARED_PREF_NAME, Context.MODE_PRIVATE);
    	mRooted = mPreferences.getBoolean(Constants.KEY_ROOTED, false);
		mSubsystemVer = mPreferences.getInt(Constants.KEY_SUBSYSTEM, -1);
    	if(!mRooted)
	        new RootCheck().execute();

		new UpdateCheck().execute();
    	
        mShare = (ImageButton)findViewById(R.id.share_button);
        mShare.setOnClickListener(new OnClickListener(){
	        @Override
			public void onClick(View v) {
	        	startActivity(Utils.share(mContext));
	        }
	    });
        
        mSearch = (ImageButton)findViewById(R.id.search_button);
        mSearch.setOnClickListener(new OnClickListener(){
	        @Override
			public void onClick(View v) {
	        	// search packages
	         }
	    });
    	
        mPager = (ViewPager)findViewById(R.id.pager);
        mPagerAdapter = new PagerAdapter(this,
                mPager,
                (PagerHeader)findViewById(R.id.pager_header));

        mPagerAdapter.addPage(PackageManager.class, R.string.page_label_pacman);
        mPagerAdapter.addPage(Homepage.class, R.string.page_label_homepage);
        mPagerAdapter.addPage(PackageBrowser.class, R.string.page_label_browse);
        mPagerAdapter.addPage(SourcesBrowser.class, R.string.page_label_sources);
        mPagerAdapter.addPage(TerminalActivity.class, R.string.page_label_terminal);

        // set the adapter to display our homepage tab
        mPagerAdapter.setDisplayedPage(Constants.HOMEPAGE_TAB_ID);
    }
    
    public void setRooted(boolean root) {
    	mRooted = root;
    	SharedPreferences.Editor editor = mPreferences.edit();
    	editor.putBoolean(Constants.KEY_ROOTED, mRooted);
    	
    	// call to maintain compatibility with older SDK versions (pre-gingerbread)
    	SharedPreferencesCompat.apply(editor);
    }

   /*
    * Most of this class is Adam Shanks (ChainsDD)
    */
    public static class PagerAdapter extends FragmentPagerAdapter
    	implements ViewPager.OnPageChangeListener, PagerHeader.OnHeaderClickListener {

    	 private final Context mContext;
         private final ViewPager mPager;
         private final PagerHeader mHeader;
         private final ArrayList<PageInfo> mPages = new ArrayList<PageInfo>();
         
         static final class PageInfo {
             private final Class<?> clss;
             private final Bundle args;
             
             PageInfo(Class<?> _clss, Bundle _args) {
                 clss = _clss;
                 args = _args;
             }
         }

         public PagerAdapter(FragmentActivity activity, ViewPager pager,
                 PagerHeader header) {
             super(activity.getSupportFragmentManager());
             mContext = activity;
             mPager = pager;
             mHeader = header;
             mHeader.setOnHeaderClickListener(this);
             mPager.setAdapter(this);
             mPager.setOnPageChangeListener(this);
         }
         
         public void setDisplayedPage(int index) {
			mPager.setCurrentItem(index);
		}

		public void addPage(Class<?> clss, int res) {
             addPage(clss, null, res);
         }
         
         public void addPage(Class<?> clss, String title) {
             addPage(clss, null, title);
         }
         
         public void addPage(Class<?> clss, Bundle args, int res) {
             addPage(clss, null, mContext.getResources().getString(res));
         }
         
         public void addPage(Class<?> clss, Bundle args, String title) {
             PageInfo info = new PageInfo(clss, args);
             mPages.add(info);
             mHeader.add(0, title);
             notifyDataSetChanged();
         }

         @Override
         public int getCount() {
             return mPages.size();
         }
         
         @Override
         public Fragment getItem(int position) {
             PageInfo info = mPages.get(position);
             return Fragment.instantiate(mContext, info.clss.getName(), info.args);
         }

         @Override
         public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
             mHeader.setPosition(position, positionOffset, positionOffsetPixels);
         }

         @Override
         public void onPageSelected(int position) {
             mHeader.setDisplayedPage(position);
         }

         @Override
         public void onPageScrollStateChanged(int state) {
         }

         @Override
         public void onHeaderClicked(int position) {
             
         }

         @Override
         public void onHeaderSelected(int position) {
             mPager.setCurrentItem(position);
         }
    }
    
    public class RootCheck extends AsyncTask<Void, Void, Void> {
    	@Override
    	protected Void doInBackground(Void... args) {
    		setRooted(Utils.isRooted());
    		return null;
    	}
    }

    public class UpdateCheck extends AsyncTask<Void, Void, Void> {
    	@Override
    	protected Void doInBackground(Void... args) {
			int ver;
    		try {
				File subsystemFile = new File(Environment.getExternalStorageDirectory(), "subsystem.zip");
				if(subsystemFile.exists())
					subsystemFile.delete();
				ver = Integer.parseInt(
				new BufferedReader(
					new InputStreamReader(
						new DefaultHttpClient()
							.execute(new HttpGet(Constants.SUBSYSTEM_MANIFEST))
							.getEntity()
							.getContent()
					))
					.readLine()
				);
				if(mSubsystemVer >= ver)
					return null;
				mSubsystemVer = ver;
				Log.i("Foundry", "Found subsystem version " + mSubsystemVer);
				new DefaultHttpClient()
					.execute(new HttpGet(Constants.SUBSYSTEM_LOCATION))
					.getEntity()
					.writeTo(
						new FileOutputStream(subsystemFile)
				);
				Log.i("Foundry", "Downloaded subsystem... extracting");
				Utils.createSubsystem(getResources());
				subsystemFile.delete();
    			SharedPreferences.Editor editor = mPreferences.edit();
				editor.putInt(Constants.KEY_SUBSYSTEM, mSubsystemVer);
				SharedPreferencesCompat.apply(editor);
				Log.d("Foundry", "Update complete!");
			} catch (Exception e) {
				e.printStackTrace();
				Log.e("Foundry", "Error checking for subsystem update! " + e.toString());
			}
    		return null;
    	}
    }
}