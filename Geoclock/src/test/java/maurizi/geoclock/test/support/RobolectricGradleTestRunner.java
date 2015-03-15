package maurizi.geoclock.test.support;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.res.Fs;

import maurizi.geoclock.BuildConfig;

public class RobolectricGradleTestRunner extends RobolectricTestRunner {

    public RobolectricGradleTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected AndroidManifest getAppManifest(Config config) {
        String myAppPath = RobolectricGradleTestRunner.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        String buildVariant = (BuildConfig.FLAVOR.isEmpty() ? "" : BuildConfig.FLAVOR + "/") + BuildConfig.BUILD_TYPE;
        String manifestPath = myAppPath + "../../../manifests/full/" + buildVariant + "/AndroidManifest.xml";
        String resPath = myAppPath + "../../../res/" + buildVariant;
        String assetPath = myAppPath + "../../../assets/" + buildVariant;

        return createAppManifest(Fs.fileFromPath(manifestPath), Fs.fileFromPath(resPath), Fs.fileFromPath(assetPath), "maurizi.geoclock");
    }
}