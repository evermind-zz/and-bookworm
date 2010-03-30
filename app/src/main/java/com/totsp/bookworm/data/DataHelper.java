package com.totsp.bookworm.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.totsp.bookworm.Constants;
import com.totsp.bookworm.model.Author;
import com.totsp.bookworm.model.Book;

import java.util.HashSet;

/**
 * Android DataHelper to encapsulate SQL and DB details.
 * Includes SQLiteOpenHelper.
 *
 * @author ccollins
 *
 */
public class DataHelper {

   public static final String ORDER_BY_TITLE_ASC = "book.tit asc";
   public static final String ORDER_BY_TITLE_DESC = "book.tit desc";
   public static final String ORDER_BY_RATING_ASC = "bookuserdata.rat asc, book.tit asc";
   public static final String ORDER_BY_RATING_DESC = "bookuserdata.rat desc, book.tit asc";

   private static final String DATABASE_NAME = "bookworm.db";
   private static final int DATABASE_VERSION = 10;
   private static final String BOOK_TABLE = "book";
   private static final String BOOKUSERDATA_TABLE = "bookuserdata";
   private static final String BOOKAUTHOR_TABLE = "bookauthor";
   private static final String AUTHOR_TABLE = "author";

   private final Context context;
   private SQLiteDatabase db;

   private final SQLiteStatement bookInsertStmt;
   private static final String BOOK_INSERT =
            "insert into " + DataHelper.BOOK_TABLE + "(" + DataConstants.ISBN10 + "," + DataConstants.ISBN13 + ","
                     + DataConstants.TITLE + "," + DataConstants.SUBTITLE + "," + DataConstants.PUBLISHER + ","
                     + DataConstants.DESCRIPTION + "," + DataConstants.FORMAT + "," + DataConstants.SUBJECT + ","
                     + DataConstants.DATEPUB + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
   private final SQLiteStatement bookAuthorInsertStmt;
   private static final String BOOKAUTHOR_INSERT =
            "insert into " + DataHelper.BOOKAUTHOR_TABLE + "(" + DataConstants.BOOKID + "," + DataConstants.AUTHORID
                     + ") values (?, ?)";

   private final SQLiteStatement authorInsertStmt;
   private static final String AUTHOR_INSERT =
            "insert into " + DataHelper.AUTHOR_TABLE + "(" + DataConstants.NAME + ") values (?)";

   private final SQLiteStatement bookUserDataInsertStmt;
   private static final String BOOKUSERDATA_INSERT =
            "insert into " + DataHelper.BOOKUSERDATA_TABLE + "(" + DataConstants.BOOKID + ","
                     + DataConstants.READSTATUS + "," + DataConstants.RATING + "," + DataConstants.BLURB
                     + ") values (?, ?, ?, ?)";

   public DataHelper(final Context context) {
      this.context = context;
      OpenHelper openHelper = new OpenHelper(this.context);
      this.db = openHelper.getWritableDatabase();

      // statements
      this.bookInsertStmt = this.db.compileStatement(DataHelper.BOOK_INSERT);
      this.bookAuthorInsertStmt = this.db.compileStatement(DataHelper.BOOKAUTHOR_INSERT);
      this.authorInsertStmt = this.db.compileStatement(DataHelper.AUTHOR_INSERT);
      this.bookUserDataInsertStmt = this.db.compileStatement(DataHelper.BOOKUSERDATA_INSERT);

      if (openHelper.isDbCreated()) {
         // insert default data here if needed
      }
   }

   public SQLiteDatabase getDb() {
      return this.db;
   }

   public void resetDbConnection() {
      Log.i(Constants.LOG_TAG, "resetting database connection (close and re-open).");
      this.cleanup();
      this.db =
               SQLiteDatabase.openDatabase("/data/data/com.totsp.bookworm/databases/bookworm.db", null,
                        SQLiteDatabase.OPEN_READWRITE);
   }

   public void cleanup() {
      if ((this.db != null) && this.db.isOpen()) {
         this.db.close();
      }
   }

   //
   // DB methods
   //

   //
   // book
   //   
   public Cursor getSelectBookJoinCursor(final String orderBy) {
      // note that query MUST have a column named _id
      String query =
               "select book.bid as _id, book.tit, book.subtit, book.pub, book.datepub, book.format, "
                        + "bookuserdata.rstat, bookuserdata.rat, bookuserdata.blurb from book "
                        + "join bookuserdata on book.bid = bookuserdata.bid";
      if (Constants.LOCAL_LOGD) {
         Log.d(Constants.LOG_TAG, "Main screen adapter cursor query - " + query);
      }
      if ((orderBy != null) && (orderBy.length() > 0)) {
         query += " order by " + orderBy;
      }
      return this.db.rawQuery(query, null);
   }

   public long insertBook(final Book b) {
      long bookId = 0L;
      if ((b != null) && (b.title != null)) {

         // TODO check for existing book using multiple criteria (not just title)
         /*
         Book bookExists = this.selectBook(b.title);
         if (bookExists != null) {
            return bookExists.getId();
         }
         */

         // use transaction
         this.db.beginTransaction();
         try {
            // insert authors as needed
            HashSet<Long> authorIds = new HashSet<Long>();
            if ((b.authors != null) && !b.authors.isEmpty()) {
               for (Author a : b.authors) {
                  Author authorExists = this.selectAuthor(a.name);
                  if (authorExists == null) {
                     authorIds.add(this.insertAuthor(a));
                  } else {
                     authorIds.add(authorExists.id);
                  }
               }
            }

            // insert book
            this.bookInsertStmt.clearBindings();
            this.bookInsertStmt.bindString(1, b.isbn10);
            this.bookInsertStmt.bindString(2, b.isbn13);
            this.bookInsertStmt.bindString(3, b.title);
            this.bookInsertStmt.bindString(4, b.subTitle);
            this.bookInsertStmt.bindString(5, b.publisher);
            this.bookInsertStmt.bindString(6, b.description);
            this.bookInsertStmt.bindString(7, b.format);
            this.bookInsertStmt.bindString(8, b.subject);
            this.bookInsertStmt.bindLong(9, b.datePubStamp);
            bookId = this.bookInsertStmt.executeInsert();
            if (Constants.LOCAL_LOGD) {
               Log.d(Constants.LOG_TAG, "book id after insert - " + bookId);
            }

            // insert bookauthors
            this.insertBookAuthorData(bookId, authorIds);

            // insert bookuserdata
            this.insertBookUserData(bookId, b.read, b.rating, b.blurb);

            this.db.setTransactionSuccessful();
         } catch (SQLException e) {
            Log.e(Constants.LOG_TAG, "Error inserting book", e);
         } finally {
            this.db.endTransaction();
         }
      } else {
         throw new IllegalArgumentException("Error, book cannot be null, and must have a unique title");
      }
      return bookId;
   }

   public void updateBook(final Book b) {
      if ((b != null) && (b.id != 0)) {
         Book bookExists = this.selectBook(b.id);
         if (bookExists == null) {
            throw new IllegalArgumentException(
                     "Cannot update book that does not already exist (for rename, delete and insert)");
         }

         // use transaction
         this.db.beginTransaction();
         try {

            // insert authors as needed            
            HashSet<Long> authorIds = new HashSet<Long>();
            if ((b.authors != null) && !b.authors.isEmpty()) {
               for (Author a : b.authors) {
                  Author authorExists = this.selectAuthor(a.name);
                  if (authorExists == null) {
                     authorIds.add(this.insertAuthor(a));
                  } else {
                     authorIds.add(authorExists.id);
                  }
               }
            }

            // update/insert book/author associations
            this.deleteBookAuthorData(b.id);
            this.insertBookAuthorData(b.id, authorIds);

            // update/insert book user data
            this.deleteBookUserData(b.id);
            this.updateBookUserData(b.id, b.read, b.rating, b.blurb);

            // update book
            final ContentValues values = new ContentValues();
            values.put(DataConstants.ISBN10, b.isbn10);
            values.put(DataConstants.ISBN13, b.isbn13);
            values.put(DataConstants.TITLE, b.title);
            values.put(DataConstants.SUBTITLE, b.subTitle);
            values.put(DataConstants.PUBLISHER, b.publisher);
            values.put(DataConstants.DESCRIPTION, b.description);
            values.put(DataConstants.FORMAT, b.format);
            values.put(DataConstants.SUBJECT, b.subject);
            values.put(DataConstants.DATEPUB, b.datePubStamp);

            this.db.update(DataHelper.BOOK_TABLE, values, DataConstants.BOOKID + " = ?", new String[] { String
                     .valueOf(b.id) });

            this.db.setTransactionSuccessful();
         } catch (SQLException e) {
            Log.e(Constants.LOG_TAG, "Error inserting book", e);
         } finally {
            this.db.endTransaction();
         }
      } else {
         throw new IllegalArgumentException("Error, book cannot be null, and must have a unique title");
      }
   }

   public Book selectBook(final long id) {
      Book b = null;
      // TODO add join to bookuserdata - rather than sep query
      Cursor c =
               this.db.query(DataHelper.BOOK_TABLE,
                        new String[] { DataConstants.ISBN10, DataConstants.ISBN13, DataConstants.TITLE,
                                 DataConstants.SUBTITLE, DataConstants.PUBLISHER, DataConstants.DESCRIPTION,
                                 DataConstants.FORMAT, DataConstants.SUBJECT, DataConstants.DATEPUB },
                        DataConstants.BOOKID + " = ?", new String[] { String.valueOf(id) }, null, null, null, "1");
      if (c.moveToFirst()) {
         b = new Book();
         b.id = id;
         try {
            b.isbn10 = (c.getString(0));
         } catch (IllegalArgumentException e) {
         }
         try {
            b.isbn13 = (c.getString(1));
         } catch (IllegalArgumentException e) {
         }
         b.title = (c.getString(2));
         b.subTitle = (c.getString(3));
         b.publisher = (c.getString(4));
         b.description = (c.getString(5));
         b.format = (c.getString(6));
         b.subject = (c.getString(7));
         b.datePubStamp = (c.getLong(8));
         b.authors = (this.selectAuthorsByBookId(id));

         Book userData = this.selectBookUserData(id);
         if (userData != null) {
            b.read = (userData.read);
            b.rating = (userData.rating);
            b.blurb = (userData.blurb);
         }
      }
      if ((c != null) && !c.isClosed()) {
         c.close();
      }
      return b;
   }

   public HashSet<Book> selectAllBooks() {
      HashSet<Book> set = new HashSet<Book>();
      // NOTE - add join to bookuserdata - rather than sep query
      Cursor c =
               this.db.query(DataHelper.BOOK_TABLE,
                        new String[] { DataConstants.BOOKID, DataConstants.ISBN10, DataConstants.ISBN13,
                                 DataConstants.TITLE, DataConstants.SUBTITLE, DataConstants.PUBLISHER,
                                 DataConstants.DESCRIPTION, DataConstants.FORMAT, DataConstants.SUBJECT,
                                 DataConstants.DATEPUB }, null, null, null, null, DataConstants.TITLE + " desc", null);
      if (c.moveToFirst()) {
         do {
            Book b = new Book();
            b.id = (c.getLong(0));
            try {
               b.isbn10 = (c.getString(1));
            } catch (IllegalArgumentException e) {
            }
            try {
               b.isbn13 = (c.getString(2));
            } catch (IllegalArgumentException e) {
            }
            b.title = (c.getString(3));
            b.subTitle = (c.getString(4));
            b.publisher = (c.getString(5));
            b.description = (c.getString(6));
            b.format = (c.getString(7));
            b.subject = (c.getString(8));
            b.datePubStamp = (c.getLong(9));
            b.authors = (this.selectAuthorsByBookId(b.id));

            Book userData = this.selectBookUserData(b.id);
            if (userData != null) {
               b.read = (userData.read);
               b.rating = (userData.rating);
               b.blurb = (userData.blurb);
            }

            set.add(b);
         } while (c.moveToNext());
      }
      if ((c != null) && !c.isClosed()) {
         c.close();
      }
      return set;
   }

   public HashSet<Book> selectAllBooksByAuthor(final String name) {
      HashSet<Book> set = new HashSet<Book>();
      Author a = this.selectAuthor(name);
      if (a != null) {
         Cursor c =
                  this.db.query(DataHelper.BOOKAUTHOR_TABLE, new String[] { DataConstants.BOOKID },
                           DataConstants.AUTHORID + " = ?", new String[] { String.valueOf(a.id) }, null, null, null);
         if (c.moveToFirst()) {
            do {
               Book b = this.selectBook(c.getLong(0));
               set.add(b);
            } while (c.moveToNext());
         }
         if ((c != null) && !c.isClosed()) {
            c.close();
         }
      }
      return set;
   }

   public HashSet<String> selectAllBookNames() {
      HashSet<String> set = new HashSet<String>();
      Cursor c =
               this.db.query(DataHelper.BOOK_TABLE, new String[] { DataConstants.TITLE }, null, null, null, null,
                        DataConstants.TITLE + " desc");
      if (c.moveToFirst()) {
         do {
            set.add(c.getString(0));
         } while (c.moveToNext());
      }
      if ((c != null) && !c.isClosed()) {
         c.close();
      }
      return set;
   }

   public void deleteBook(final long id) {
      Book b = this.selectBook(id);
      if (b != null) {
         HashSet<Author> authors = this.selectAuthorsByBookId(id);
         this.db.delete(DataHelper.BOOKAUTHOR_TABLE, DataConstants.BOOKID + " = ?",
                  new String[] { String.valueOf(b.id) });
         this.db.delete(DataHelper.BOOK_TABLE, DataConstants.BOOKID + " = ?", new String[] { String.valueOf(id) });
         // if no other books by same author, also delete author
         for (Author a : authors) {
            HashSet<Book> books = this.selectAllBooksByAuthor(a.name);
            if (books.isEmpty()) {
               this.deleteAuthor(a.id);
            }
         }
      }
   }

   //
   // book-author data
   //   
   public void insertBookAuthorData(final long bookId, final HashSet<Long> authorIds) {
      for (Long authorId : authorIds) {
         this.bookAuthorInsertStmt.clearBindings();
         this.bookAuthorInsertStmt.bindLong(1, bookId);
         this.bookAuthorInsertStmt.bindLong(2, authorId);
         this.bookAuthorInsertStmt.executeInsert();
      }
   }

   public void deleteBookAuthorData(final long bookId) {
      this.db.delete(DataHelper.BOOKAUTHOR_TABLE, DataConstants.BOOKID + " = ?",
               new String[] { String.valueOf(bookId) });
   }

   //
   // book-user data
   //
   public Book selectBookUserData(final long bookId) {
      Book passData = null;
      Cursor c =
               this.db.query(DataHelper.BOOKUSERDATA_TABLE, new String[] { DataConstants.READSTATUS,
                        DataConstants.RATING, DataConstants.BLURB }, DataConstants.BOOKID + " = ?",
                        new String[] { String.valueOf(bookId) }, null, null, null, "1");
      if (c.moveToFirst()) {
         passData = new Book();
         passData.read = (c.getInt(0) == 0 ? false : true);
         passData.rating = (c.getInt(1));
         passData.blurb = (c.getString(2));
      }
      if ((c != null) && !c.isClosed()) {
         c.close();
      }
      return passData;
   }

   public long insertBookUserData(final long bookId, final boolean readStatus, final int rating, final String blurb) {
      this.bookUserDataInsertStmt.clearBindings();
      this.bookUserDataInsertStmt.bindLong(1, bookId);
      this.bookUserDataInsertStmt.bindLong(2, readStatus ? 1 : 0);
      this.bookUserDataInsertStmt.bindLong(3, rating);
      if (blurb != null) {
         this.bookUserDataInsertStmt.bindString(4, blurb);
      }
      long id = 0L;
      try {
         id = this.bookUserDataInsertStmt.executeInsert();
      } catch (SQLiteConstraintException e) {
         // TODO sometimes constraint occurs, seems to be related to db versions, not sure
         // for now catch and update instead (hack)
         this.updateBookUserData(bookId, readStatus, rating, blurb);
      }
      return id;
   }

   public void updateBookUserData(final long bookId, final boolean readStatus, final int rating, final String blurb) {
      // insert in case not present - if book was added before this was avail, etc
      Book existingData = this.selectBookUserData(bookId);
      if (existingData == null) {
         this.insertBookUserData(bookId, readStatus, rating, blurb);
      } else {
         final ContentValues values = new ContentValues();
         values.put(DataConstants.READSTATUS, readStatus ? 1 : 0);
         values.put(DataConstants.RATING, rating);
         values.put(DataConstants.BLURB, blurb);
         this.db.update(DataHelper.BOOKUSERDATA_TABLE, values, DataConstants.BOOKID + " = ?", new String[] { String
                  .valueOf(bookId) });
      }
   }

   public void deleteBookUserData(final long bookId) {
      if (bookId > 0) {
         this.db.delete(DataHelper.BOOKUSERDATA_TABLE, DataConstants.BOOKID + " = ?", new String[] { String
                  .valueOf(bookId) });
      }
   }

   //
   // author
   //
   public long insertAuthor(final Author a) {
      this.authorInsertStmt.clearBindings();
      this.authorInsertStmt.bindString(1, a.name);
      return this.authorInsertStmt.executeInsert();
   }

   public Author selectAuthor(final long id) {
      Author a = null;
      Cursor c =
               this.db.query(DataHelper.AUTHOR_TABLE, new String[] { DataConstants.NAME }, DataConstants.AUTHORID
                        + " = ?", new String[] { String.valueOf(id) }, null, null, null, "1");
      if (c.moveToFirst()) {
         a = new Author();
         a.id = (id);
         a.name = (c.getString(0));
      }
      if ((c != null) && !c.isClosed()) {
         c.close();
      }
      return a;
   }

   public Author selectAuthor(final String name) {
      Author a = null;
      Cursor c =
               this.db.query(DataHelper.AUTHOR_TABLE, new String[] { DataConstants.AUTHORID }, DataConstants.NAME
                        + " = ?", new String[] { name }, null, null, null, "1");
      if (c.moveToFirst()) {
         a = this.selectAuthor(c.getLong(0));
      }
      if ((c != null) && !c.isClosed()) {
         c.close();
      }
      return a;
   }

   public HashSet<Author> selectAuthorsByBookId(final long bookId) {
      HashSet<Author> authors = new HashSet<Author>();
      HashSet<Long> authorIds = new HashSet<Long>();
      Cursor c =
               this.db.query(DataHelper.BOOKAUTHOR_TABLE, new String[] { DataConstants.AUTHORID }, DataConstants.BOOKID
                        + " = ?", new String[] { String.valueOf(bookId) }, null, null, null, null);
      if (c.moveToFirst()) {
         do {
            authorIds.add(c.getLong(0));
         } while (c.moveToNext());
      }
      if ((c != null) && !c.isClosed()) {
         c.close();
      }
      if (!authorIds.isEmpty()) {
         for (Long authorId : authorIds) {
            Author a = this.selectAuthor(authorId);
            if (a != null) {
               authors.add(a);
            }
         }
      }
      return authors;
   }

   public HashSet<Author> selectAllAuthors() {
      HashSet<Author> set = new HashSet<Author>();
      Cursor c =
               this.db.query(DataHelper.AUTHOR_TABLE, new String[] { DataConstants.AUTHORID, DataConstants.NAME },
                        null, null, null, null, DataConstants.NAME + " desc", null);
      if (c.moveToFirst()) {
         do {
            Author a = new Author();
            a.id = (c.getLong(0));
            a.name = (c.getString(1));
            set.add(a);
         } while (c.moveToNext());
      }
      if ((c != null) && !c.isClosed()) {
         c.close();
      }
      return set;
   }

   public void deleteAuthor(final long id) {
      Author a = this.selectAuthor(id);
      if (a != null) {
         this.db.delete(DataHelper.AUTHOR_TABLE, DataConstants.AUTHORID + " = ?", new String[] { String.valueOf(id) });
      }
   }

   public void deleteAuthor(final String name) {
      Author a = this.selectAuthor(name);
      if (a != null) {
         this.db.delete(DataHelper.AUTHOR_TABLE, DataConstants.NAME + " = ?", new String[] { name });
      }
   }

   // super delete - clears all tables
   public void deleteAllDataYesIAmSure() {
      Log.i(Constants.LOG_TAG, "deleting all data from database - deleteAllYesIAmSure invoked");
      this.db.beginTransaction();
      try {
         this.db.delete(DataHelper.AUTHOR_TABLE, null, null);
         this.db.delete(DataHelper.BOOKAUTHOR_TABLE, null, null);
         this.db.delete(DataHelper.BOOKUSERDATA_TABLE, null, null);
         this.db.delete(DataHelper.BOOK_TABLE, null, null);
         this.db.setTransactionSuccessful();
      } finally {
         this.db.endTransaction();
      }
      this.db.execSQL("vacuum");
   }

   //
   // end DB methods
   //  

   //
   // SQLiteOpenHelper   
   //

   private static class OpenHelper extends SQLiteOpenHelper {

      private boolean dbCreated;

      OpenHelper(final Context context) {
         super(context, DataHelper.DATABASE_NAME, null, DataHelper.DATABASE_VERSION);
      }

      @Override
      public void onCreate(final SQLiteDatabase db) {

         if (Constants.LOCAL_LOGD) {
            Log.d(Constants.LOG_TAG, "onCreate DataHelper.OpenHelper");
         }

         // using StringBuilder here because it is easier to read/reuse lines
         StringBuilder sb = new StringBuilder();

         // book table
         sb.append("CREATE TABLE " + DataHelper.BOOK_TABLE + " (");
         sb.append(DataConstants.BOOKID + " INTEGER PRIMARY KEY, ");
         sb.append(DataConstants.ISBN10 + " TEXT, ");
         sb.append(DataConstants.ISBN13 + " TEXT, ");
         sb.append(DataConstants.TITLE + " TEXT, ");
         sb.append(DataConstants.SUBTITLE + " TEXT, ");
         sb.append(DataConstants.PUBLISHER + " TEXT, ");
         sb.append(DataConstants.DESCRIPTION + " TEXT, ");
         sb.append(DataConstants.FORMAT + " TEXT, ");
         sb.append(DataConstants.SUBJECT + " TEXT, ");
         sb.append(DataConstants.DATEPUB + " INTEGER");
         sb.append(");");
         db.execSQL(sb.toString());

         // author table
         sb.setLength(0);
         sb.append("CREATE TABLE " + DataHelper.AUTHOR_TABLE + " (");
         sb.append(DataConstants.AUTHORID + " INTEGER PRIMARY KEY, ");
         sb.append(DataConstants.NAME + " TEXT");
         sb.append(");");
         db.execSQL(sb.toString());

         // bookauthor table
         sb.setLength(0);
         sb.append("CREATE TABLE " + DataHelper.BOOKAUTHOR_TABLE + " (");
         sb.append(DataConstants.BOOKAUTHORID + " INTEGER PRIMARY KEY, ");
         sb.append(DataConstants.BOOKID + " INTEGER, ");
         sb.append(DataConstants.AUTHORID + " INTEGER, ");
         sb.append("FOREIGN KEY(" + DataConstants.BOOKID + ") REFERENCES " + DataHelper.BOOK_TABLE + "("
                  + DataConstants.BOOKID + "), ");
         sb.append("FOREIGN KEY(" + DataConstants.AUTHORID + ") REFERENCES " + DataHelper.AUTHOR_TABLE + "("
                  + DataConstants.AUTHORID + ") ");
         sb.append(");");
         db.execSQL(sb.toString());

         // bookdata table (users book data, ratings, reviews, etc)
         sb.setLength(0);
         sb.append("CREATE TABLE " + DataHelper.BOOKUSERDATA_TABLE + " (");
         sb.append(DataConstants.BOOKUSERDATAID + " INTEGER PRIMARY KEY, ");
         sb.append(DataConstants.BOOKID + " INTEGER, ");
         sb.append(DataConstants.READSTATUS + " INTEGER, ");
         sb.append(DataConstants.RATING + " INTEGER, ");
         sb.append(DataConstants.BLURB + " TEXT, ");
         sb.append("FOREIGN KEY(" + DataConstants.BOOKID + ") REFERENCES " + DataHelper.BOOK_TABLE + "("
                  + DataConstants.BOOKID + ") ");
         sb.append(");");
         db.execSQL(sb.toString());

         // constraints 
         // (ISBN cannot be unique - some books don't have em - use TITLE unique?)
         //db.execSQL("CREATE UNIQUE INDEX uidxBookTitle ON " + BOOK_TABLE + "(" + DataConstants.TITLE
         //         + " COLLATE NOCASE)");
         db.execSQL("CREATE UNIQUE INDEX uidxAuthorName ON " + DataHelper.AUTHOR_TABLE + "(" + DataConstants.NAME
                  + " COLLATE NOCASE)");
         db.execSQL("CREATE UNIQUE INDEX uidxBookIdForUserData ON " + DataHelper.BOOKUSERDATA_TABLE + "("
                  + DataConstants.BOOKID + " COLLATE NOCASE)");

         this.dbCreated = true;
      }

      @Override
      public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
         Log
                  .i(Constants.LOG_TAG, "SQLiteOpenHelper onUpgrade - oldVersion:" + oldVersion + " newVersion:"
                           + newVersion);
         // export old data first, then upgrade, then import
         db.execSQL("DROP TABLE IF EXISTS " + DataHelper.BOOK_TABLE);
         db.execSQL("DROP TABLE IF EXISTS " + DataHelper.AUTHOR_TABLE);
         db.execSQL("DROP TABLE IF EXISTS " + DataHelper.BOOKUSERDATA_TABLE);
         db.execSQL("DROP TABLE IF EXISTS " + DataHelper.BOOKAUTHOR_TABLE);
         this.onCreate(db);
      }

      public boolean isDbCreated() {
         return this.dbCreated;
      }
   }

}