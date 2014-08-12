package de.skubware.opentraining.activity.create_workout.upload_exercise;

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Map;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.RequestInterceptor.RequestFacade;
import retrofit.RestAdapter.LogLevel;
import retrofit.android.AndroidLog;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit.mime.MimeUtil;
import retrofit.mime.TypedByteArray;
import retrofit.mime.TypedInput;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.skubware.opentraining.BuildConfig;
import de.skubware.opentraining.R;
import de.skubware.opentraining.activity.create_workout.ExerciseTypeDetailFragment;
import de.skubware.opentraining.basic.ExerciseType;
import de.skubware.opentraining.basic.Muscle;
import de.skubware.opentraining.basic.SportsEquipment;
import de.skubware.opentraining.db.rest.ExerciseTypeGSONSerializer;
import de.skubware.opentraining.db.rest.LanguageGSONDeserializer;
import de.skubware.opentraining.db.rest.MuscleGSONDeserializer;
import de.skubware.opentraining.db.rest.ServerModel;
import de.skubware.opentraining.db.rest.SportsEquipmentGSONDeserializer;
import de.skubware.opentraining.db.rest.ServerModel.Equipment;
import de.skubware.opentraining.db.rest.ServerModel.Language;
import de.skubware.opentraining.db.rest.ServerModel.MuscleCategory;

public class UploadExerciseAsyncTask extends AsyncTask<ExerciseType, Void, Throwable> {
	private final ExerciseTypeDetailFragment mFragment;
	private final ProgressDialog mDialog;


	/**
	 * @param exerciseTypeDetailFragment
	 */
	public UploadExerciseAsyncTask(ExerciseTypeDetailFragment exerciseTypeDetailFragment) {
		mFragment = exerciseTypeDetailFragment;
		mDialog = new ProgressDialog(mFragment.getActivity());
	}


	  
	  
	  protected void onPreExecute() {
		     this.mDialog.setMessage("Uploading exercise ...");
		     this.mDialog.show();
		  }
	  
	
	/**
	 * @return Null if everything went fine, the original exception otherwise.
	 */
	@Override
	protected Throwable doInBackground(ExerciseType... exercise) {

		// prepare GsonBuilder
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(ExerciseType.class, new ExerciseTypeGSONSerializer());
		gsonBuilder.registerTypeAdapter(ServerModel.Equipment[].class, new SportsEquipmentGSONDeserializer());
		gsonBuilder.registerTypeAdapter(ServerModel.MuscleCategory[].class, new MuscleGSONDeserializer());
		gsonBuilder.registerTypeAdapter(ServerModel.Language[].class, new LanguageGSONDeserializer());
		gsonBuilder.setPrettyPrinting();

		Gson gson = gsonBuilder.create();

		GsonConverter converter = new GsonConverter(gson);

		
		RestAdapter.Builder builder = new RestAdapter.Builder().setConverter(converter)
				.setRequestInterceptor(new RequestInterceptor() {
					@Override
					public void intercept(RequestFacade requestFacade) {
						requestFacade.addHeader("Authorization", "Token ba1ce753f54ba3b8ee4af301f07c58628a1c01bf");
					}
				});


		String baseURL;
		if (BuildConfig.DEBUG) {
			// set different API-Endpoint for debugging
			baseURL = "http://preview.wger.de/api/v2/";
			// only log if debug-build
			// (otherwise auth-token appears in log)
			builder.setLog(new AndroidLog("WgerRestService")).setLogLevel(LogLevel.FULL);
		}else{
			// get the URL from preferences
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mFragment.getActivity().getApplicationContext());
			baseURL = settings.getString("exercise_sync_url", mFragment.getActivity().getApplicationContext().getString(R.string.pref_default_exercise_sync_url));
		}
		builder.setEndpoint(baseURL + "/api/v2/");

		
		RestAdapter restAdapter = builder.build();

		WgerRestService service = restAdapter.create(WgerRestService.class);

		// get server model of SportsEquipment
		ServerModel.Equipment[] serverEquipment = service.getEquipment();
		Map<SportsEquipment, Equipment> eqMap = Equipment.getEquipmentMap(serverEquipment, mFragment.getActivity());
		ExerciseTypeGSONSerializer.setEquipmentMap(eqMap);

		// get server model of Muscle(categories)
		ServerModel.MuscleCategory[] serverMuscles = service.getMuscles();
		Map<Muscle, MuscleCategory> muscleMap = MuscleCategory.getMuscleMap(serverMuscles, mFragment.getActivity());
		ExerciseTypeGSONSerializer.setMuscleMap(muscleMap);

		for (Muscle m : muscleMap.keySet()) {
			Log.e(ExerciseTypeDetailFragment.TAG, m.toString() + " = " + muscleMap.get(m) + "\n");
		}

		// get server model of Languages
		ServerModel.Language[] serverLanguages = service.getLanguages();
		Map<Locale, Language> languageMap = Language.getLanguageMap(serverLanguages, mFragment.getActivity());
		ExerciseTypeGSONSerializer.setLanguageMap(languageMap);

		try {
			service.createExercise(exercise[0]);		
		} catch (RetrofitError retEr) {
			if(retEr.getCause() != null)
				return retEr.getCause();
			else
				return retEr;
		}
		
		return null;
	}

	@Override
	protected void onPostExecute(Throwable ex) {
		mDialog.dismiss();

		AlertDialog.Builder alertDialog = new AlertDialog.Builder(mFragment.getActivity());
		String msg;
		String title;
		if (ex == null) {
			// everything went fine
			title = "Upload successfull";
			msg = "Upload finished";
		} else {
			title = "Upload failed";
			if(ex instanceof RetrofitError){
				// show server response to user
				Response response = ((RetrofitError) ex).getResponse();
				msg = response.getReason() + ": " + getBodyString(response);
			}else{
				// show custom error message if problem is known
				msg = ex.getMessage();
			}	
		}
		alertDialog.setMessage(msg);
		alertDialog.setTitle(title);
		alertDialog.create().show();
	}

	@Override
	protected void onProgressUpdate(Void... values) {
	}
	
	
	/* Helper method for parsing the response body */
	private String getBodyString(Response response) {

		TypedInput body = response.getBody();

		if (body != null) {

			if (!(body instanceof TypedByteArray)) {
				Log.e(ExerciseTypeDetailFragment.TAG, "Could not parse.");
				return "";
			}

			byte[] bodyBytes = ((TypedByteArray) body).getBytes();
			String bodyMime = body.mimeType();
			String bodyCharset = MimeUtil.parseCharset(bodyMime);
			try {
				return new String(bodyBytes, bodyCharset);
			} catch (UnsupportedEncodingException e) {
				Log.e(ExerciseTypeDetailFragment.TAG, "Could not parse.");
				return "";
			}
		}
		return null;

	}
	
}