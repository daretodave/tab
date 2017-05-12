package blue.dave.tab;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class TABDebugActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_debug);
    }

    public void debugGlobalUpdater() {
        TABGroupProviderUpdater updater = new TABGroupProviderUpdater(this);
        updater.setTextViewText(R.id.tab_status, "DEBUG");
        updater.persist();
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.debug_global_updater:
                //DEBUG: Global Updater
                debugGlobalUpdater();
                break;
            case R.id.debug_task:
                //DEBUG: TAB Task sent from TAB Base Provider
                TABBaseProvider.update(this, TABTask.DEBUG);
                break;
        }
    }
}
