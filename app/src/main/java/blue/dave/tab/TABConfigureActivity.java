package blue.dave.tab;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.viewpagerindicator.LinePageIndicator;

public class TABConfigureActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener {

    private static final int PAGES = 2;

    public static final String EXTRA_BROADCAST_FROM_CONFIGURE = "FROM_CONFIGURE";

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        setContentView(R.layout.activity_tab_configure);
        setResult(RESULT_CANCELED);

        ViewPager mPager = (ViewPager) findViewById(R.id.pager);
        assert mPager != null;
        mPager.setAdapter(new TABConfigureSlidePagerAdapter(getSupportFragmentManager()));
        mPager.addOnPageChangeListener(this);

        LinePageIndicator indicator = (LinePageIndicator) findViewById(R.id.indicator);
        assert indicator != null;
        indicator.setViewPager(mPager);

        onPageSelected(mPager.getCurrentItem());

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.configure_exit:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.configure, menu);
        return true;
    }

    @Override
    public void onPageSelected(int position) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && getSupportActionBar() != null) {
            switch (position) {
                case 0:
                    getSupportActionBar().setSubtitle(getString(R.string.configure_subtitle_intro));
                    break;
                case 1:
                    getSupportActionBar().setSubtitle(getString(R.string.configure_subtitle_credentials));
                    break;
            }
        }
    }

    private class TABConfigureSlidePagerAdapter extends FragmentStatePagerAdapter {

        public TABConfigureSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch(position) {
                case 0:
                    return new TABConfigureWelcomeFragment();
                case 1:
                    return new TABConfigureCredentialsFragment();
            }
            return null;
        }

        @Override
        public int getCount() {
            return PAGES;
        }

    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

}
