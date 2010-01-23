package com.totsp.bookworm.data;

import android.util.Log;

import com.totsp.bookworm.Splash;
import com.totsp.bookworm.model.Author;
import com.totsp.bookworm.model.Book;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;

/**
 * SAX DefaultHandler impl for Google Books feed.
 * 
 * TODO - compare with XMLPullParser impl - though direct SAX may still be faster?
 * http://www.developer.com/xml/article.php/3824221/Android-XML-Parser-Performance.htm 
 *
 * @author ccollins
 */
public class GoogleBooksHandler extends DefaultHandler {

   private static final String ENTRY = "entry";

   private ArrayList<Book> books;

   private Book book;

   private boolean inEntry;
   private boolean inTitle;
   private boolean inDate;
   private boolean inCreator;

   public GoogleBooksHandler() {
      this.books = new ArrayList<Book>();
   }

   @Override
   public void startDocument() throws SAXException {
   }

   @Override
   public void endDocument() throws SAXException {
   }

   @Override
   public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {      
      if (localName.equals(GoogleBooksHandler.ENTRY)) {
         this.inEntry = true;
         this.book = new Book();
      }

      if (this.inEntry && localName.equals("title")) {
         this.inTitle = true;
      } else if (this.inEntry && localName.equals("dc:date")) {
         this.inDate = true;
      } else if (this.inEntry && localName.equals("dc:creator")) {
         this.inCreator = true;
      }
   }

   @Override
   public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
      if (localName.equals(GoogleBooksHandler.ENTRY)) {
         if (this.inEntry) {
            this.books.add(this.book);
            this.inEntry = false;
         }
      }

      if (this.inEntry && localName.equals("title")) {
         this.inTitle = false;
      } else if (this.inEntry && localName.equals("dc:date")) {
         this.inDate = false;
      } else if (this.inEntry && localName.equals("dc:creator")) {
         this.inCreator = false;
      }
   }

   @Override
   public void characters(char ch[], int start, int length) {

      if (this.inEntry && this.inTitle) {
         this.book.setTitle(new String(ch, start, length));
      } else if (this.inEntry && this.inDate) {
         // parse date
      } else if (this.inEntry && this.inCreator) {
         this.book.getAuthors().add(new Author(new String(ch, start, length)));
      }

   }

   private String getAttributeValue(String attName, Attributes atts) {
      String result = null;
      for (int i = 0; i < atts.getLength(); i++) {
         String thisAtt = atts.getLocalName(i);
         if (attName.equals(thisAtt)) {
            result = atts.getValue(i);
            break;
         }
      }
      return result;
   }

   public ArrayList<Book> getBooks() {
      return this.books;
   }
}
