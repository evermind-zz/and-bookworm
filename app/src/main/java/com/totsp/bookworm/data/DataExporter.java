package com.totsp.bookworm.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import com.totsp.bookworm.Constants;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Android DataExporter that allows the passed in SQLiteDatabase 
 * to be exported to an XML file (optionally on the external
 * storage [sdcard] or as a private file in the application's context).
 * 
 * @author ccollins
 *
 */
public class DataExporter {

   private final Context context;
   private SQLiteDatabase db;
   private XmlBuilder xmlBuilder;

   public DataExporter(final Context ctx, SQLiteDatabase db) {
      this.context = ctx;
      this.db = db;
   }

   public void export(String exportFileName, boolean toSdCard) throws IOException {
      // TODO get dbName from db?
      String dbName = "tempdbname";
      Log.i(Constants.LOG_TAG, "exporting database - " + dbName + " fileName=" + exportFileName + " toSdCard="
               + toSdCard);

      if (toSdCard && !this.isSdAvail()) {
         // TODO warn the user, SD not avail
      }
      
      this.xmlBuilder = new XmlBuilder();
      this.xmlBuilder.start(dbName);

      // get the tables
      String sql = "select * from sqlite_master";
      Cursor c = this.db.rawQuery(sql, new String[0]);
      Log.d(Constants.LOG_TAG, "select * from sqlite_master, cur size " + c.getCount());
      if (c.moveToFirst()) {
         do {
            String tableName = c.getString(c.getColumnIndex("name"));
            Log.d(Constants.LOG_TAG, "table name " + tableName);

            // skip metadata, sequence, and uidx (user indexes)
            if (!tableName.equals("android_metadata") && !tableName.equals("sqlite_sequence")
                     && !tableName.startsWith("uidx")) {
               this.exportTable(tableName);
            }
         } while (c.moveToNext());
      }
      String xmlString = this.xmlBuilder.end();
      this.writeToFile(xmlString, exportFileName, toSdCard);
      Log.i(Constants.LOG_TAG, "exporting database complete");
   }

   private void exportTable(final String tableName) throws IOException {
      Log.d(Constants.LOG_TAG, "exporting table - " + tableName);
      this.xmlBuilder.openTable(tableName);
      String sql = "select * from " + tableName;
      Cursor c = this.db.rawQuery(sql, new String[0]);
      if (c.moveToFirst()) {
         int cols = c.getColumnCount();
         do {
            this.xmlBuilder.openRow();
            for (int i = 0; i < cols; i++) {
               this.xmlBuilder.addColumn(c.getColumnName(i), c.getString(i));
            }
            this.xmlBuilder.closeRow();
         } while (c.moveToNext());
      }
      c.close();
      this.xmlBuilder.closeTable();
   }

   private void writeToFile(String xmlString, String exportFileName, boolean toSdCard) throws IOException {
      FileWriter fw = null;
      // TODO handle files better, existing, etc
      if (toSdCard) {
         File dir = new File (Environment.getExternalStorageDirectory(), "bookwormdata");
         if (!dir.exists()) {
            dir.mkdirs();
         }
         File file = new File(dir, exportFileName + ".xml");
         file.createNewFile();
         fw = new FileWriter(file);
      } else {
         File dir = DataExporter.this.context.getDir("bookwormdata", Context.MODE_PRIVATE);
         if (!dir.exists()) {
            dir.mkdirs();
         }
         File file = new File(dir, exportFileName + ".xml");
         file.createNewFile();
         fw = new FileWriter(file);
      }
      if (fw != null) {
         fw.write(xmlString);
         fw.flush();
         fw.close();
      }
   }

   private boolean isSdAvail() {
      return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
   }

   /**
    * XmlBuilder is used to write XML tags (open and close, and a few attributes)
    * to a StringBuilder. Here we have nothing to do with IO or SQL, just a fancy StringBuilder. 
    * 
    * @author ccollins
    *
    */
   class XmlBuilder {
      private static final String CLOSE_WITH_TICK = "'>";
      private static final String DB_OPEN = "<database name='";
      private static final String DB_CLOSE = "</database>";
      private static final String TABLE_OPEN = "<table name='";
      private static final String TABLE_CLOSE = "</table>";
      private static final String ROW_OPEN = "<row>";
      private static final String ROW_CLOSE = "</row>";
      private static final String COL_OPEN = "<col name='";
      private static final String COL_CLOSE = "</col>";

      private final StringBuilder sb;

      public XmlBuilder() throws IOException {
         this.sb = new StringBuilder();
      }

      void start(String dbName) {
         this.sb.append(DB_OPEN + dbName + CLOSE_WITH_TICK);
      }

      String end() throws IOException {
         this.sb.append(DB_CLOSE);
         return this.sb.toString();
      }

      void openTable(String tableName) {
         this.sb.append(TABLE_OPEN + tableName + CLOSE_WITH_TICK);
      }

      void closeTable() {
         this.sb.append(TABLE_CLOSE);
      }

      void openRow() {
         this.sb.append(ROW_OPEN);
      }

      void closeRow() {
         this.sb.append(ROW_CLOSE);
      }

      void addColumn(final String name, final String val) throws IOException {
         this.sb.append(COL_OPEN + name + CLOSE_WITH_TICK + val + COL_CLOSE);
      }
   }
}
