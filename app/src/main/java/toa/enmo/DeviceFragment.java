package toa.enmo;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.w3c.dom.Text;

/**
 * Created by iosdev on 23.9.2016.
 */

public class DeviceFragment extends Fragment {

    TextView tempText;
    TextView accText;
    TextView accelText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.device_fragment, container, false);

        accelText = (TextView) v.findViewById(R.id.accelText);
        tempText = (TextView) v.findViewById(R.id.temperatureText);
        accText = (TextView) v.findViewById(R.id.accText);

        return v;
    }
}
