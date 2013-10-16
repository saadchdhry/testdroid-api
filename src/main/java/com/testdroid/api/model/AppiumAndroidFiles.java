package com.testdroid.api.model;

import com.testdroid.api.APIEntity;
import com.testdroid.api.APIException;
import java.io.File;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonIgnore;

/**
 *
 * @author Jarno Tuovinen <jarno.tuovinen@bitbar.com>
 */
@XmlRootElement
public class AppiumAndroidFiles extends APIFiles {
    private AndroidAppFile appiumApp;

    public AppiumAndroidFiles() {}

    public AppiumAndroidFiles(Long id, DataFile data, AndroidAppFile appiumApp) {
        super(id, data);
        this.appiumApp = appiumApp;
    }

    public AndroidAppFile getAppiumApp() {
        return appiumApp;
    }

    public void setAppiumApp(AndroidAppFile appiumApp) {
        this.appiumApp = appiumApp;
    }

    public void uploadApp(File file) throws APIException {
        this.appiumApp = client.postFile(getApplicationURI(), "application/vnd.android.package-archive", file, AndroidAppFile.class);
    }

    @Override
    @JsonIgnore
    protected <T extends APIEntity> void clone(T from) {
        super.clone(from);
        AppiumAndroidFiles appiumFiles = (AppiumAndroidFiles) from;
        this.appiumApp = appiumFiles.appiumApp;
    }
}
