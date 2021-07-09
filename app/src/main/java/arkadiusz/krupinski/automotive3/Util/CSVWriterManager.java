package arkadiusz.krupinski.automotive3.Util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.SyncStateContract;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class CSVWriterManager {

    Context context;
    Path path;

    public CSVWriterManager(Context context) {
        this.context = context;
    }

    public void createFile(String filename) throws IOException {
        // check if permitted to do some actions
        if (ContextCompat.checkSelfPermission(
                context.getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            return;
        }

        File pathfile = new File(context.getExternalFilesDir(null).getAbsolutePath() + "/csv");

        if (!pathfile.isDirectory()) {
//              if no directory, create one
            pathfile.mkdir();
        }

        File file = new File(pathfile,
                File.separator + filename + ".csv");
        if (!file.exists()) {
//              if file does not exist, create one
            file.createNewFile();
            this.path = file.toPath();
            Log.i("File created: ", file.getName());
        }

    }

    public void csvWriterOneByOne(List<String[]> stringArray) throws Exception {
        // check if permitted to do some actions
        if (ContextCompat.checkSelfPermission(
                context.getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            return;
        }

        CSVWriter writer = new CSVWriter(new FileWriter(this.path.toString()));
        for (String[] array : stringArray) {
            writer.writeNext(array);
        }
        writer.close();
//        return SyncStateContract.Helpers.readFile(path);
    }

    public void csvWriterOnce(String[] strings) throws Exception {
        CSVWriter writer = new CSVWriter(new FileWriter(this.path.toString()));
//        for (String[] array : stringArray) {
        writer.writeNext(strings);
//        }
        writer.close();
//        return SyncStateContract.Helpers.readFile(path);
    }

}
