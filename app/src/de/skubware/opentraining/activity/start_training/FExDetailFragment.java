package de.skubware.opentraining.activity.start_training;

/**
 * 
 * This is OpenTraining, an Android application for planning your your fitness training.
 * Copyright (C) 2012-2013 Christian Skubich
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

import java.util.List;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import de.skubware.opentraining.R;
import de.skubware.opentraining.activity.create_workout.ExerciseDetailOnGestureListener;
import de.skubware.opentraining.basic.FitnessExercise;
import de.skubware.opentraining.basic.TrainingEntry;
import de.skubware.opentraining.basic.TrainingSubEntry;
import de.skubware.opentraining.basic.Workout;
import de.skubware.opentraining.db.DataHelper;
import de.skubware.opentraining.db.DataProvider;
import de.skubware.opentraining.db.IDataProvider;

/**
 * A fragment representing a single Exercise detail screen. This fragment is
 * either contained in a {@link FExListActivity} in two-pane mode (on
 * tablets) or a {@link FExDetailActivity} on handsets.
 */
public class FExDetailFragment extends SherlockFragment implements DialogFragmentAddEntry.Callbacks {
	/** Tag for logging */
	public static final String TAG = FExDetailFragment.class.getName();
	

	/**
	 * The fragment argument representing the item ID that this fragment
	 * represents.
	 */
	public static final String ARG_FEX = "f_ex";
	
	public static final String ARG_WORKOUT = "workout";


	/**
	 * The {@link FitnessExercise} this fragment is presenting.
	 */
	private FitnessExercise mExercise;
	
	/** Currently shown {@link Workout}. */
	private Workout mWorkout;
	
	private GestureDetector mGestureScanner;

	
	/**
	 * Mandatory empty constructor for the fragment manager to instantiate the
	 * fragment (e.g. upon screen orientation changes).
	 */
	public FExDetailFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.setHasOptionsMenu(true);
		
		mExercise = (FitnessExercise) getArguments().getSerializable(ARG_FEX);
		mWorkout = (Workout) getArguments().getSerializable(ARG_WORKOUT);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_fex_detail, container, false);

		// show the current exercise
		ImageView imageview = (ImageView) rootView.findViewById(R.id.imageview);
		
		// set gesture detector
		this.mGestureScanner = new GestureDetector(this.getActivity(), new ExerciseDetailOnGestureListener(this, imageview, mExercise));

		// Images
		if (!mExercise.getImagePaths().isEmpty()) {
			DataHelper data = new DataHelper(getActivity());
			imageview.setImageDrawable(data.getDrawable(mExercise.getImagePaths().get(0).toString()));
		} else {
			imageview.setImageResource(R.drawable.ic_launcher);
		}

		// Image license
		TextView image_license = (TextView) rootView.findViewById(R.id.textview_image_license);
		if (mExercise.getImageLicenseMap().values().iterator().hasNext()) {
			image_license.setText(mExercise.getImageLicenseMap().values().iterator().next());
		} else {
			image_license.setText("Keine Lizenzinformationen vorhanden");
		}

		imageview.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return mGestureScanner.onTouchEvent(event);
			}
		});
		
		
		EditText editText = (EditText) rootView.findViewById(R.id.edittext_current_entry);
		editText.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				List<TrainingSubEntry> subEntryList = mExercise.getTrainingEntryList().get(0).getSubEntryList();
				// create new SubEntry if there is none
				if(subEntryList.isEmpty()){
					showDialog();
					return;
				}
				
				// edit existing SubEntries if there are some
				AlertDialog.Builder builder_subentry_chooser = new AlertDialog.Builder(getActivity());
				builder_subentry_chooser.setTitle(getString(R.string.choose_subentry));

				final ArrayAdapter<TrainingSubEntry> adapter = new ArrayAdapter<TrainingSubEntry>(getActivity(), android.R.layout.select_dialog_singlechoice,
						subEntryList);

				builder_subentry_chooser.setAdapter(adapter, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						TrainingSubEntry choosenSubEntry = adapter.getItem(item);
						showDialog(choosenSubEntry);
					}

				});
				builder_subentry_chooser.create().show();

			}
		});
		
		return rootView;
	}
	
	@Override
	public void onStart(){
		super.onStart();
		updateTrainingEntries();	
	}	
	
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.fex_detail_menu, menu);

		// configure menu_item_add_entry
		MenuItem menu_item_add_entry = (MenuItem) menu.findItem(R.id.menu_item_add_entry);
		menu_item_add_entry.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				showDialog();			
				return true;
			}
		});
	}
	
	/** Shows DialogFragmentAddEntry. */
	private void showDialog() {
		showDialog(null);
	}
	
	/**
	 * Shows DialogFragmentAddEntry with the given subEntry. If subEntry is null
	 * a new SubEntry will be added to {@link mExercise}.
	 */
	private void showDialog(TrainingSubEntry subEntry) {

		// DialogFragment.show() will take care of adding the fragment
		// in a transaction. We also want to remove any currently showing
		// dialog, so make our own transaction and take care of that here.
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		Fragment prev = getFragmentManager().findFragmentByTag("dialog");
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);

		// Create and show the dialog.
		DialogFragment newFragment = DialogFragmentAddEntry.newInstance(mExercise, subEntry);
		newFragment.show(ft, "dialog");
	}
	

	@Override
	public void onEntryEdited(FitnessExercise fitnessExercise) {
		Log.d(TAG, "onEntryEdited()");
						
		mWorkout.updateFitnessExercise(fitnessExercise);

		IDataProvider dataProvider = new DataProvider(getActivity());
		dataProvider.saveWorkout(mWorkout);
		
		FExListFragment fragment = (FExListFragment) getFragmentManager().findFragmentById(R.id.exercise_list);
		if(fragment != null){
			Log.d(TAG, "updating FExListFragment");
			// either notify list fragment if it's there (on tablets)
			fragment.setWorkout(mWorkout);
		}else{
			Log.d(TAG, "setting Intent for FExListActivity");
			// or return intent if list fragment is not visible (on small screens)
			Intent i = new Intent();
			i.putExtra(FExListActivity.ARG_WORKOUT, mWorkout);
			this.getActivity().setResult(Activity.RESULT_OK, i);		
		}
		
		
		mExercise = fitnessExercise;
		updateTrainingEntries();
	}
	
	/**
	 * Updates the displayed {@link TrainingEntry}. That means the text of all
	 * {@link TrainingSubEntrys} is updated.
	 */
	private void updateTrainingEntries(){
		EditText editText = (EditText) this.getActivity().findViewById(R.id.edittext_current_entry);

		List<TrainingSubEntry> subEntryList = mExercise.getTrainingEntryList().get(0).getSubEntryList();
		if(subEntryList.isEmpty()){
			editText.setText(null);
			return;
		}
		
		StringBuilder content = new StringBuilder();
		for(TrainingSubEntry entry:subEntryList){
			content.append(entry.getContent());
			content.append("\n");
		}
		// finally delete last "\n"
		content.deleteCharAt(content.length() - 1);

		
		editText.setText(content.toString());
	}
	
}