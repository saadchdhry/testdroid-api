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
public class AppiumIosFiles extends APIFiles {
    private IOSAppFile iosApp;

    public AppiumIosFiles() {}

    public AppiumIosFiles(Long id, DataFile data, IOSAppFile appiumApp) {
        super(id, data);
        this.iosApp = appiumApp;
    }

    public IOSAppFile getIosApp() {
        return iosApp;
    }

    public void setIosApp(IOSAppFile appiumApp) {
        this.iosApp = appiumApp;
    }

    public void uploadApp(File file) throws APIException {
        this.iosApp = client.postFile(getApplicationURI(), "application/vnd.android.package-archive", file, IOSAppFile.class);
    }

    @Override
    @JsonIgnore
    protected <T extends APIEntity> void clone(T from) {
        super.clone(from);
        AppiumIosFiles appiumFiles = (AppiumIosFiles) from;
        this.iosApp = appiumFiles.iosApp;
    }
}
