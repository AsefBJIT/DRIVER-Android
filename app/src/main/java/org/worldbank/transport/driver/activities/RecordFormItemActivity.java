package org.worldbank.transport.driver.activities;

import android.app.ActionBar;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.azavea.androidvalidatedforms.tasks.ValidationTask;

import org.worldbank.transport.driver.R;
import org.worldbank.transport.driver.utilities.RecordFormSectionManager;


/**
 * Form for an item in a section that contains multiple elements.
 *
 * Created by kathrynkillebrew on 12/31/15.
 */
public class RecordFormItemActivity extends RecordFormActivity {

    private static final String LOG_LABEL = "FormItemActivity";
    public static final String ITEM_INDEX = "driver_form_item_index";

    private int itemIndex;

    @Override
    protected Object getModelObject() {
        return RecordFormSectionManager.getOrCreateListItem(sectionField, sectionClass, currentlyEditing, itemIndex);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Bundle bundle = getIntent().getExtras();
        itemIndex = bundle.getInt(ITEM_INDEX);
        super.onCreate(savedInstanceState);
    }

    /**
     * Helper to build a layout with previous/next/save buttons.
     *
     * @return View with buttons with handlers added to it
     */
    @Override
    public RelativeLayout buildButtonBar() {
        // reference to this, for use in button actions (validation task makes weak ref)
        final RecordFormActivity thisActivity = this;

        // put buttons in a relative layout for positioning on right or left
        RelativeLayout buttonBar = new RelativeLayout(this);
        buttonBar.setId(R.id.record_button_bar_id);
        buttonBar.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT));

        // add 'delete' button
        Button deleteBtn = new Button(this);
        RelativeLayout.LayoutParams backBtnLayoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);

        backBtnLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        deleteBtn.setLayoutParams(backBtnLayoutParams);

        deleteBtn.setId(R.id.record_back_button_id);
        deleteBtn.setText(getText(R.string.record_delete_button));
        buttonBar.addView(deleteBtn);

        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_LABEL, "Delete button clicked");

                if (RecordFormSectionManager.deleteListItem(sectionField, sectionClass, currentlyEditing, itemIndex)) {
                    Toast toast = Toast.makeText(thisActivity, getString(R.string.record_item_delete_success), Toast.LENGTH_SHORT);
                    toast.show();
                    finish();
                } else {
                    Toast toast = Toast.makeText(thisActivity, getString(R.string.record_item_delete_failure), Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        });

        // add save button
        Button goBtn = new Button(this);
        RelativeLayout.LayoutParams goBtnLayoutParams = new RelativeLayout.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT);
        goBtnLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        goBtn.setLayoutParams(goBtnLayoutParams);

        goBtn.setText(R.string.record_save_button);
        goBtn.setId(R.id.record_save_button_id);

        buttonBar.addView(goBtn);

        goBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(LOG_LABEL, "Save button clicked");
                goPrevious = false;
                new ValidationTask(thisActivity).execute();
            }
        });

        return buttonBar;
    }

    @Override
    public void proceed() {
        // item is saved on model already; simply close this view
        finish();
    }
}
