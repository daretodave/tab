package blue.dave.tab;

import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

public class TABProviderUpdater {

    private RemoteViews views;
    private ComponentName name;

    public TABProviderUpdater(Context context, Class provider, int layout) {
        views = new RemoteViews(context.getPackageName(), layout);
        name = new ComponentName(context, provider);
    }

    public RemoteViews getViews() {
        return views;
    }

    public ComponentName getName() {
        return name;
    }

}
