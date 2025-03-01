package org.worldbank.transport.driver.datastore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

import org.worldbank.transport.driver.staticmodels.DriverConstantFields;
import org.worldbank.transport.driver.staticmodels.Record;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


/**
 * Use this class to access the records database.
 *
 * Created by kathrynkillebrew on 1/5/16.
 */
public class RecordDatabaseManager {

    private static final String LOG_LABEL = "DatabaseManager";

    private static final String DATABASE_NAME = "driverdb";

    // store date/time strings in same format generated by defaulting to current timestamp
    private static final DateFormat storeDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    // use as WHERE clause to match on ID
    private static final String WHERE_ID = "_id= ?";

    // use to return all columns from record table
    private static final String[] ALL_FIELDS = {
            DriverRecordContract.RecordEntry._ID,
            DriverRecordContract.RecordEntry.COLUMN_ENTERED_AT,
            DriverRecordContract.RecordEntry.COLUMN_UPDATED_AT,
            DriverRecordContract.RecordEntry.COLUMN_SCHEMA_VERSION,
            DriverRecordContract.RecordEntry.COLUMN_DATA,

            // constant fields
            DriverRecordContract.RecordEntry.COLUMN_OCCURRED_FROM,
            DriverRecordContract.RecordEntry.COLUMN_OCCURRED_TO,
            DriverRecordContract.RecordEntry.COLUMN_LATITUDE,
            DriverRecordContract.RecordEntry.COLUMN_LONGITUDE,
            DriverRecordContract.RecordEntry.COLUMN_WEATHER,
            DriverRecordContract.RecordEntry.COLUMN_LIGHT
    };

    RecordDatabaseHelper dbHelper;

    private final SQLiteDatabase writableDb;
    private final SQLiteDatabase readableDb;

    /**
     * Set up database for use. Will use in-memory database if amTesting flag is true.
     *
     * @param context Context for database
     * @param amTesting Use in-memory DB if true, otherwise use file-based DB.
     */
    public RecordDatabaseManager(Context context, boolean amTesting) {
        if (amTesting) {
            Log.w(LOG_LABEL, "DB Manager will use in-memory DB. This should only happen in testing!");
            dbHelper = new RecordDatabaseHelper(context, null);
        } else {
            dbHelper = new RecordDatabaseHelper(context, DATABASE_NAME);
        }

        writableDb = dbHelper.getWritableDatabase();
        readableDb = dbHelper.getReadableDatabase();

        storeDateFormat.setTimeZone(TimeZone.getDefault());
    }

    /**
     * Add a record to the database.
     *
     * @param schemaVersion UUID of the schema used to create the record
     * @param data Serialized JSON representation of the record
     * @return The row ID of the added record
     */
    public long addRecord(String schemaVersion, String data, DriverConstantFields constantFields) {

        // store constants
        ContentValues values = createConstantContent(constantFields);

        // add schema version and serialized record data
        values.put(DriverRecordContract.RecordEntry.COLUMN_SCHEMA_VERSION, schemaVersion);
        values.put(DriverRecordContract.RecordEntry.COLUMN_DATA, data);

        writableDb.beginTransaction();
        long newId = -1;
        try {
            newId = writableDb.insert(DriverRecordContract.RecordEntry.TABLE_NAME, null, values);
            writableDb.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(LOG_LABEL, "Database record insert failed");
            e.printStackTrace();
            newId = -1;
        } finally {
            writableDb.endTransaction();
        }

        return newId;
    }

    /**
     * Helper to build create/update query components for the constant fields.
     * Call first, then modify returned values set to add/modify other fields.
     *
     * @param constantFields Object with constant fields to set
     * @return New set of query values containing the constant fields
     */
    private ContentValues createConstantContent(DriverConstantFields constantFields) {
        ContentValues values = new ContentValues();

        // convert date to property formatted UTC strings
        String formattedDateOccurred = storeDateFormat.format(constantFields.occurredFrom);
        values.put(DriverRecordContract.RecordEntry.COLUMN_OCCURRED_FROM, formattedDateOccurred);

        // set occurred-to same as occurred-from
        // TODO: make this separately editable, or keep it hidden?
        values.put(DriverRecordContract.RecordEntry.COLUMN_OCCURRED_TO, formattedDateOccurred);

        // TODO: remove/modify null check if field becomes required
        if (constantFields.location != null) {
            values.put(DriverRecordContract.RecordEntry.COLUMN_LATITUDE, constantFields.location.getLatitude());
            values.put(DriverRecordContract.RecordEntry.COLUMN_LONGITUDE, constantFields.location.getLongitude());
        }

        if (constantFields.Weather != null) {
            values.put(DriverRecordContract.RecordEntry.COLUMN_WEATHER, constantFields.Weather.toString());
        }

        if (constantFields.Light != null) {
            values.put(DriverRecordContract.RecordEntry.COLUMN_LIGHT, constantFields.Light.toString());
        }

        return values;
    }

    /**
     * Update an existing record in the database. Should be called on record 'save'.
     *
     * @param data Serialized JSON string of the DriverSchema object to save
     * @param recordId Database ID of the record to update
     * @return Number of rows affected (should be 1 on success)
     */
    public int updateRecord(String data, DriverConstantFields constantFields, long recordId) {

        String[] whereArgs = { String.valueOf(recordId) };

        // store constants
        ContentValues values = createConstantContent(constantFields);

        values.put(DriverRecordContract.RecordEntry.COLUMN_DATA, data);

        // set last updated timestamp
        values.put(DriverRecordContract.RecordEntry.COLUMN_UPDATED_AT, storeDateFormat.format(new Date()));

        writableDb.beginTransaction();
        int affected = -1;
        try {
            affected = writableDb.update(DriverRecordContract.RecordEntry.TABLE_NAME, values, WHERE_ID, whereArgs);
            writableDb.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(LOG_LABEL, "Database record update failed for ID " + recordId);
            e.printStackTrace();
            affected = -1;
        } finally {
            writableDb.endTransaction();
        }
        return affected;
    }

    /**
     * Delete a single record from the database. To be called after record successfully uploaded.
     *
     * @param recordId Database ID for the record to delete
     * @return true on success
     */
    public boolean deleteRecord(long recordId) {
        String[] whereArgs = { String.valueOf(recordId) };

        writableDb.beginTransaction();
        int affected = -1;
        try {
            affected = writableDb.delete(DriverRecordContract.RecordEntry.TABLE_NAME, WHERE_ID, whereArgs);
            writableDb.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(LOG_LABEL, "Database record deletion failed for ID " + recordId);
            e.printStackTrace();
            affected = -1;
        } finally {
            writableDb.endTransaction();
        }

        if (affected == 1) {
            return true;
        } else {
            Log.e(LOG_LABEL, "Number or records affected by delete: " + affected);
        }

        return false;
    }

    /**
     * Get a cursor to fetch all records.
     *
     * @return Database cursor to retrieve all records
     */
    public Cursor readAllRecords() {

        String sortOrder = DriverRecordContract.RecordEntry.COLUMN_ENTERED_AT + " DESC";

        return readableDb.query(
                DriverRecordContract.RecordEntry.TABLE_NAME,
                ALL_FIELDS, // columns
                null,       // WHERE
                null,       // WHERE args
                null,       // GROUP BY
                null,       // HAVING
                sortOrder   // ORDER BY
        );
    }

    /**
     * Fetch the JSON representation of a record from the database by its _id
     *
     * @param recordId Database ID for record to get
     * @return Serialized string of the record data, or null on failure
     */
    public String getSerializedRecordWithId(long recordId) {

        String[] dataField = { DriverRecordContract.RecordEntry.COLUMN_DATA };
        String[] whereArgs = { String.valueOf(recordId) };

        Cursor cursor = readableDb.query(
                DriverRecordContract.RecordEntry.TABLE_NAME,
                dataField, // columns
                WHERE_ID,   // WHERE
                whereArgs,  // WHERE args
                null,       // GROUP BY
                null,       // HAVING
                null        // ORDER BY
        );

        if (!cursor.moveToFirst()) {
            Log.e(LOG_LABEL, "Record with ID " + recordId + " not found!");
            return null;
        }

        String recordData = cursor.getString(0);
        cursor.close();
        return recordData;
    }

    /**
     * Get a cursor that queries for a single database record
     * @param recordId ID for the record
     * @return Database cursor that should have one record in it
     */
    public Cursor getRecordByIdCursor(long recordId) {
        String[] whereArgs = { String.valueOf(recordId) };

        return readableDb.query(
                DriverRecordContract.RecordEntry.TABLE_NAME,
                ALL_FIELDS, // columns
                WHERE_ID,   // WHERE
                whereArgs,  // WHERE args
                null,       // GROUP BY
                null,       // HAVING
                null        // ORDER BY
        );
    }

    /**
     * Retrieve a single record by ID from the database
     * @param recordId ID for the record
     * @return deserialized record, with constants
     */
    public Record getRecordById(long recordId) {

        Cursor cursor = getRecordByIdCursor(recordId);

        if (!cursor.moveToFirst()) {
            Log.e(LOG_LABEL, "Record with ID " + recordId + " not found!");
            return null;
        }

        // find column offsets in response
        int dataColumn = cursor.getColumnIndex(DriverRecordContract.RecordEntry.COLUMN_DATA);
        int schemaColumn = cursor.getColumnIndex(DriverRecordContract.RecordEntry.COLUMN_SCHEMA_VERSION);

        // fetch fields
        String recordData = cursor.getString(dataColumn);
        DriverConstantFields constants = readStoredConstants(cursor);
        String schemaVersion = cursor.getString(schemaColumn);
        cursor.close();

        Object recordObject;
        if (recordData == null) {
            Log.e(LOG_LABEL, "Cannot deserialize null record data string!");
        }

        recordObject = DriverSchemaSerializer.readRecord(recordData);
        if (recordObject == null) {
            Log.e(LOG_LABEL, "Failed to deserialize record data for id " + recordId);
        }

        return new Record(recordObject, recordId, constants, schemaVersion);
    }

    /**
     * Helper to rebuild constant fields object from a retrieved record.
     *
     * @param cursor Read cursor at record to read with constant fields
     * @return new DriverConstantFields object with fields set
     */
    private DriverConstantFields readStoredConstants(Cursor cursor) {
        DriverConstantFields constantFields = new DriverConstantFields();

        // get field offsets
        int occurredFromColumn = cursor.getColumnIndex(DriverRecordContract.RecordEntry.COLUMN_OCCURRED_FROM);
        int longitudeColumn = cursor.getColumnIndex(DriverRecordContract.RecordEntry.COLUMN_LONGITUDE);
        int latitudeColumn = cursor.getColumnIndex(DriverRecordContract.RecordEntry.COLUMN_LATITUDE);
        int weatherColumn = cursor.getColumnIndex(DriverRecordContract.RecordEntry.COLUMN_WEATHER);
        int lightColumn = cursor.getColumnIndex(DriverRecordContract.RecordEntry.COLUMN_LIGHT);

        String occurredFromString = cursor.getString(occurredFromColumn);
        try {
            constantFields.occurredFrom = storeDateFormat.parse(occurredFromString);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        constantFields.location = new Location("");
        constantFields.location.setLatitude(cursor.getDouble(latitudeColumn));
        constantFields.location.setLongitude(cursor.getDouble(longitudeColumn));

        String weatherString = cursor.getString(weatherColumn);
        String lightString = cursor.getString(lightColumn);

        if (weatherString != null) {
            constantFields.Weather = DriverConstantFields.WeatherEnum.fromValue(weatherString);
        }

        if (lightString != null) {
            constantFields.Light = DriverConstantFields.LightEnum.fromValue(lightString);
        }

        return constantFields;
    }
}
