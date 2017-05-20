package de.privatepublic.pi.synth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.comm.ControlMessageDispatcher;
import de.privatepublic.pi.synth.comm.MidiHandler;


public class PresetHandler {

	private static final Logger log = LoggerFactory.getLogger(PresetHandler.class);
	
	public static enum PatchCategory {
		POLY, PAD, LEAD, BASS, KEYS, VOX, EFFECTS, PERC, PLUCKED, WHATEVER;
		
		public static PatchCategory find(String s) {
			for (PatchCategory p:PatchCategory.values()) {
				if (p.name().equals(s)) {
					return p;
				}
			}
			return WHATEVER;
		}
	};
	
	private static File PATCH_DIR = new File(System.getProperty("user.home"), "synthpi");
	
	public static void initDirectories() {
		if (P.CUSTOM_SETTINGS_DIR!=null) {
			PATCH_DIR = new File(P.CUSTOM_SETTINGS_DIR, "synthpi");
		}
		try {
			FileUtils.forceMkdir(PATCH_DIR);
			if (PATCH_DIR.exists()) {
				log.info("File directory for local patches and settings: {}", PATCH_DIR);
			}
			else {
				log.warn("Could not create file directory for local patches and settings: {}", PATCH_DIR);
			}
		} catch (IOException e) {
			log.error("Could not create patch directory {}", e, PATCH_DIR);
		}		
	}
	
	
	public static void initPatch() {
		P.setToDefaults();
		P.LAST_LOADED_PATCH_NAME = "(INITIALIZED)";
		P.LAST_LOADED_PATCH_CATEGORY = PatchCategory.WHATEVER;
		SynthPi.uiMessage("Patch initialized to defaults");
	}
	
	private static JSONObject createJSONFromCurrentPatch(String name, PatchCategory category) {
		JSONObject preset = new JSONObject();
		preset.put(K.PATCH_NAME.key(), name);
		preset.put(K.PATCH_CATEGORY.key(), category);
		preset.put(K.PATCH_DATE.key(), System.currentTimeMillis());
		JSONArray params = new JSONArray();
		for (int i=0; i<P.PARAM_STORE_SIZE;i++) {
			if (P.OSC_PATH[i]!=null) { // only performance parameters
				JSONObject param = new JSONObject();
				param.put(K.PATCH_PARAM_INDEX.key(), i);
				param.put(K.PATCH_PARAM_VAL.key(), P.VAL[i]);
				params.put(param);
			}
		}
		preset.put(K.PATCH_PARAMS_LIST.key(), params);
		return preset;
	}
	
	
	public static int loadPatchFromProgramNumber(int programNo) {
		JSONObject allPatches = listPatchFiles(true);
//		JSONArray list = null;
		String pid = null;
		JSONArray fList = allPatches.optJSONArray(K.UI_FACTORY_PATCH_LIST.key());
		JSONArray uList = allPatches.optJSONArray(K.UI_USER_PATCH_LIST.key());
		int maxCount = fList.length() + uList.length();
		int appliedNo = programNo;
		if (maxCount>0) {
			if (programNo<uList.length()) {
				pid = "u"+(programNo%uList.length());
			}
			else {
				int fNo = (programNo-uList.length());
				if (fNo>=fList.length()) {
					appliedNo = programNo-(fNo-fList.length()+1);
					fNo = fList.length()-1;
				}
				pid = "f"+fNo;
			}
			loadPatchWithId(pid);
		}
		return appliedNo;
//		
//		if (programNo<64) {
//			// 0-63 factory presets
//			list = allPatches.optJSONArray(K.UI_FACTORY_PATCH_LIST.key());
//			if (list.length()>0) {
//				programNo = programNo%list.length();
//				pid = "f"+programNo;
//			}
//		}
//		else {
//			// 64-127 user presets
//			list = allPatches.optJSONArray(K.UI_USER_PATCH_LIST.key());
//			if (list.length()>0) {
//				programNo = (programNo-64)%list.length();
//				pid = "u"+programNo;
//			}
//		}
//		if (list!=null && pid!=null) {
//			loadPatchWithId(pid);
//		}
	}
	

	// TODO Make json and list handling please more reasonable and optimized (need another library)
	
	public static boolean loadPatchWithId(String pid) {
		try {
			JSONObject allPatches = listPatchFiles(true);
			for (String listKey: new String[]{ K.UI_FACTORY_PATCH_LIST.key(), K.UI_USER_PATCH_LIST.key() }) {
				JSONArray list = allPatches.optJSONArray(listKey);
				if (list!=null) {
					for (int i=0;i<list.length();i++) {
						JSONObject patch = list.getJSONObject(i);
						if (patch.getString(K.UI_PATCH_ID.key()).equals(pid)) {
							JSONArray params = patch.getJSONArray(K.PATCH_PARAMS_LIST.key());
							P.setToDefaults();
							for (int ix=0;ix<params.length();++ix) {
								try {
									JSONObject param = params.getJSONObject(ix);
									int pindex = param.getInt(K.PATCH_PARAM_INDEX.key());
									float val = (float) param.getDouble(K.PATCH_PARAM_VAL.key());
									P.set(pindex, val);
								} catch (JSONException e) {
									log.debug("Error reading param at pos "+ix, e);
								}
							}
							P.LAST_LOADED_PATCH_NAME = patch.getString(K.PATCH_NAME.key());
							P.LAST_LOADED_PATCH_CATEGORY = PatchCategory.find(patch.getString(K.PATCH_CATEGORY.key()));
							log.debug("Loaded patch {}", P.LAST_LOADED_PATCH_NAME);
							SynthPi.uiMessage("Loaded patch: "+P.LAST_LOADED_PATCH_NAME);
							SynthPi.uiLCDMessage(P.LAST_LOADED_PATCH_NAME, "PATCH LOADED");
							return true;
						}
					}
				}
			}
		} catch (JSONException e) {
			log.error("Error loading patch", e);
		}
		return false;
	}
	
	public static boolean saveCurrentOrDeletePatch(String json) {
		JSONObject meta = new JSONObject(json);
		if (meta.getString(K.UI_ACTION.key()).equals(K.UI_ACTION_DELETE.key())) {
			return deletePatch(meta.getString(K.UI_OVERWRITE_ID.key()));
		}
		try {
			return saveCurrentPatch(meta.getString(K.PATCH_NAME.key()), meta.getString(K.PATCH_CATEGORY.key()), meta.optString(K.UI_OVERWRITE_ID.key()));
		} catch (JSONException e) {
			log.warn("Could not save with meta", json, e);
			return false;
		}
	}
	
	private static boolean deletePatch(String id) {
		JSONArray userData;
		try {
			userData = readContent(userDataInputStream());
		} catch (FileNotFoundException e) {
			userData = new JSONArray();
		}
		
		int deleteIndex = Integer.valueOf(id.substring(1));
		log.info("Deleting patch #{}", deleteIndex);
		JSONObject removed = (JSONObject)userData.remove(deleteIndex);
		
		// write file
		try {
			FileUtils.write(userDataFile(), userData.toString(2), "utf-8");
			SynthPi.uiMessage("Deleted patch: "+removed.getString(K.PATCH_NAME.key()));
			log.info("Saved user data");
			return true;
		} catch (IOException e) {
			log.error("Error writing patch file", e);
		}
		return false;
	}
	
	private static boolean saveCurrentPatch(String name, String category, String overWriteId) {
		JSONArray userData;
		try {
			userData = readContent(userDataInputStream());
		} catch (FileNotFoundException e) {
			userData = new JSONArray();
		}
		PatchCategory cat;
		try {
			cat = PatchCategory.valueOf(category);
		} catch (Exception e) {
			cat = PatchCategory.WHATEVER;
		}
		JSONObject patch = createJSONFromCurrentPatch(name, cat);
		
		int overWriteIndex = -1; // -1 = append to list end
		if (overWriteId!=null) {
			try {
				overWriteIndex = Integer.valueOf(overWriteId.substring(1));

			} catch (Exception e) {
				// in case of error add to end of list
			}
		}
		if (overWriteIndex>=0) {
			if (overWriteIndex>userData.length()) {
				userData.put(patch);	
			}
			else {
				userData.put(overWriteIndex, patch);
			}
		}
		else {
			userData.put(patch);
		}
		
		// write file
		try {
			FileUtils.write(userDataFile(), userData.toString(2), "utf-8");
			log.info("Saved patch: {} to user data",name);
			SynthPi.uiMessage("Saved patch: "+name+" ("+cat+")");
			P.LAST_LOADED_PATCH_NAME = name;
			P.LAST_LOADED_PATCH_CATEGORY = cat;
			ControlMessageDispatcher.INSTANCE.updateAllParams();
		} catch (IOException e) {
			log.error("Error writing patch file", e);
		}
		return false;
	}
	
	public static JSONObject patchSaveInfo() {
		JSONObject info = new JSONObject();
		try {
			InputStream userData = userDataInputStream();
			info.put(K.UI_EXISTINGPATCHES.key(), createPatchList(readContent(userData), "u", false));
		} catch (FileNotFoundException e) {
			log.warn("No user patches file found!");
		} catch (JSONException e) {
			log.warn("Error reading user patch file.");
		}
		JSONArray catlist = new JSONArray();
		for (PatchCategory cat:PatchCategory.values()) {
			catlist.put(cat.toString());
		}
		info.put(K.UI_CATEGORIES.key(), catlist);
		info.put(K.UI_SELECTED_CATEGORY.key(), P.LAST_LOADED_PATCH_CATEGORY.toString());
		return info;
	}
	
	public static JSONObject listPatchFiles(boolean complete) {
		JSONObject result = new JSONObject();
		InputStream factoryData = PresetHandler.class.getResourceAsStream("/factory-patches.json");
		result.put(K.UI_FACTORY_PATCH_LIST.key(), createPatchList(readContent(factoryData), "f", complete));
		try {
			InputStream userData = userDataInputStream();
			result.put(K.UI_USER_PATCH_LIST.key(), createPatchList(readContent(userData), "u", complete));
		} catch (FileNotFoundException e) {
			log.warn("No user patches file found!");
		} catch (JSONException e) {
			log.warn("Error reading user patch file.");
		}
		return result;
	}
	
	private static JSONArray createPatchList(JSONArray input, String prefix, boolean complete) {
		JSONArray outlist = new JSONArray();
		if (input!=null) {
			for (int i=0;i<input.length();i++) {
				JSONObject p = input.getJSONObject(i);
				JSONObject entry = new JSONObject();
				entry.put(K.PATCH_NAME.key(), p.getString(K.PATCH_NAME.key()));
				entry.put(K.PATCH_CATEGORY.key(), p.getString(K.PATCH_CATEGORY.key()));
				entry.put(K.UI_PATCH_ID.key(), prefix+i);
				if (complete) {
					entry.put(K.PATCH_PARAMS_LIST.key(), p.getJSONArray(K.PATCH_PARAMS_LIST.key()));
				}
				outlist.put(entry);
			}
		}
		return outlist;
	}
	
	private static JSONArray readContent(InputStream in) {
		JSONArray result = null;
		try {
			String data = IOUtils.toString(in, "utf-8");
			result = new JSONArray(data);
			// TODO sort here!
			List<JSONObject> list = new ArrayList<JSONObject>();
			for (int i=0;i<result.length();i++) {
				list.add(result.getJSONObject(i));
			}
			Collections.sort(list, new Comparator<JSONObject>() {
				@Override
				public int compare(JSONObject a, JSONObject b) {
					String aCat = a.getString(K.PATCH_CATEGORY.key());
					String bCat = b.getString(K.PATCH_CATEGORY.key());
					int catComp = aCat.compareTo(bCat);
					if (catComp!=0)
						return catComp;
					String aName = a.getString(K.PATCH_NAME.key());
					String bName = b.getString(K.PATCH_NAME.key());
					int nameComp = aName.compareTo(bName);
					return nameComp;
				}
			});
			return new JSONArray(list);
		} catch (Exception e) {
			log.error("Could not read patch file", e);
		} 
		return result;
	}
	
	private static InputStream userDataInputStream() throws FileNotFoundException {
		return new FileInputStream(userDataFile());
	}
	
	private static File userDataFile() {
		return new File(PATCH_DIR, "mk2-user-patches.json");
	}
	
	private static File userSettingsFile() {
		return new File(PATCH_DIR, "mk2-settings.json");
	}
	
	private static File userRecentFile() {
		return new File(PATCH_DIR, "mk2-recent.json");
	}

	public static String settingsJSON() {
		return settingsJSONObject().toString()+"\n";
	}
	
	private static JSONObject settingsJSONObject() {
		JSONObject settings = new JSONObject();
		settings.put(K.PREF_MIDI_CHANNEL.key(), P.MIDI_CHANNEL+1);
		settings.put(K.PREF_PITCH_BEND_RANGE.key(), (int)(P.BEND_RANGE_CENTS/100));
		settings.put(K.PREF_PITCH_BEND_FIX.key(), !P.FIX_STRANGE_MIDI_PITCH_BEND);
		settings.put(K.PREF_POLYPHONY_VOICES.key(), P.POLYPHONY);
		settings.put(K.PREF_AUDIO_BUFFER_SIZE.key(), P.SAMPLE_BUFFER_SIZE);
		settings.put(K.PREF_TRANSFER_PERFORMANCE_DATA.key(), P.HTTP_SEND_PERFORMACE_DATA);
		settings.put(K.PREF_LIMITER_ENABLED.key(), P.LIMITER_ENABLED);
		return settings;
	}
	
	public static void loadSettings() {
		try {
			String jsonString = IOUtils.toString(new FileInputStream(userSettingsFile()), "utf-8");
			updateSettings(jsonString);
		} catch (FileNotFoundException e) {
			log.info("No user settings found!");
		} catch (IOException e) {
			log.warn("Error reading user settings {}\n{}", userSettingsFile(), e);
		}
	}
	
	public static void saveSettings() {
		JSONObject settings = settingsJSONObject();
		settings.put(K.PREF_MIDI_CC_MAPPING_LIST.key(), Arrays.asList(ArrayUtils.toObject(MidiHandler.getMidiMappings())));
		settings.put(K.PREF_MIDI_CC_SELECT.key(), MidiHandler.CC_PARAM_SELECT);
		settings.put(K.PREF_MIDI_CC_VALUE.key(), MidiHandler.CC_PARAM_VALUE);
		// write file
		try {
			FileUtils.write(userSettingsFile(), settings.toString(2), "utf-8");
			log.info("Saved settings to {}", userSettingsFile());
			SynthPi.uiMessage("Saved settings");
		} catch (IOException e) {
			log.error("Error writing settings file", e);
		}
	}
	
	public static void updateSettings(String jsonString) {
		log.debug("Updating settings");
		JSONObject settings = new JSONObject(jsonString);
		if (settings.has(K.PREF_MIDI_CHANNEL.key())) {
			P.MIDI_CHANNEL = settings.getInt(K.PREF_MIDI_CHANNEL.key())-1;
		}
		if (settings.has(K.PREF_PITCH_BEND_RANGE.key())) {
			P.BEND_RANGE_CENTS = settings.getInt(K.PREF_PITCH_BEND_RANGE.key())*100f;
		}
		if (settings.has(K.PREF_PITCH_BEND_FIX.key())) {
			P.FIX_STRANGE_MIDI_PITCH_BEND = !settings.getBoolean(K.PREF_PITCH_BEND_FIX.key());
		}
		if (settings.has(K.PREF_POLYPHONY_VOICES.key())) {
			P.POLYPHONY = settings.getInt(K.PREF_POLYPHONY_VOICES.key());
		}
		if (settings.has(K.PREF_TRANSFER_PERFORMANCE_DATA.key())) {
			P.HTTP_SEND_PERFORMACE_DATA = settings.getBoolean(K.PREF_TRANSFER_PERFORMANCE_DATA.key());
		}
		if (settings.has(K.PREF_LIMITER_ENABLED.key())) {
			P.LIMITER_ENABLED = settings.getBoolean(K.PREF_LIMITER_ENABLED.key());
		}
		if (settings.has(K.PREF_AUDIO_BUFFER_SIZE.key())) {
			P.SAMPLE_BUFFER_SIZE = settings.getInt(K.PREF_AUDIO_BUFFER_SIZE.key());
		}
//		if (settings.has(K.PREF_MIDI_CC_SELECT.key())) {
//			MidiHandler.CC_PARAM_SELECT = settings.getInt(K.PREF_MIDI_CC_SELECT.key());
//		}
//		if (settings.has(K.PREF_MIDI_CC_VALUE.key())) {
//			MidiHandler.CC_PARAM_VALUE = settings.getInt(K.PREF_MIDI_CC_VALUE.key());
//		}
//		if (settings.has(K.PREF_MIDI_CC_MAPPING_LIST.key())) {
//			try {
//				JSONArray ja = settings.getJSONArray(K.PREF_MIDI_CC_MAPPING_LIST.key());
//				for (int i=0;i<ja.length();i++) {
//					MidiHandler.getMidiMappings()[i] = ja.getInt(i);
//				}
//			} catch (JSONException | ArrayIndexOutOfBoundsException e) {
//				log.warn("Error reading midi mappings", e);
//			}
//		}
	}
	
	public static void updateAndSaveSettings(String jsonString) {
		updateSettings(jsonString);
		saveSettings();
	}
	
	public static void saveCurrentPatch() {
		log.info("Saving current patch ...");
		JSONObject current = createJSONFromCurrentPatch(P.LAST_LOADED_PATCH_NAME, P.LAST_LOADED_PATCH_CATEGORY);
		try {
			FileUtils.write(userRecentFile(), current.toString(2), "utf-8");
		} catch (JSONException | IOException e) {
			log.warn("Error writing current patch to {}: {}", userRecentFile(), e);
		}
	}
	
	public static void loadRecentPatch() {
		try {
			JSONObject patch = new JSONObject(FileUtils.readFileToString(userRecentFile(), "utf-8"));
			JSONArray params = patch.getJSONArray(K.PATCH_PARAMS_LIST.key());
			P.setToDefaults();
			for (int ix=0;ix<params.length();++ix) {
				try {
					JSONObject param = params.getJSONObject(ix);
					int pindex = param.getInt(K.PATCH_PARAM_INDEX.key());
					float val = (float) param.getDouble(K.PATCH_PARAM_VAL.key());
					P.set(pindex, val);
				} catch (JSONException e) {
					log.debug("Error reading param at pos "+ix, e);
				}
			}
			P.LAST_LOADED_PATCH_NAME = patch.getString(K.PATCH_NAME.key());
			P.LAST_LOADED_PATCH_CATEGORY = PatchCategory.find(patch.getString(K.PATCH_CATEGORY.key()));
			log.debug("Loaded recent patch {}", P.LAST_LOADED_PATCH_NAME);
			SynthPi.uiMessage("Loaded recent patch: "+P.LAST_LOADED_PATCH_NAME);
			SynthPi.uiLCDMessage(P.LAST_LOADED_PATCH_NAME, "RECENT PATCH");
		} catch (JSONException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static enum K {
		PATCH_NAME("name"), 
		PATCH_CATEGORY("category"), 
		PATCH_DATE("date"), 
		PATCH_PARAM_INDEX("index"), 
		PATCH_PARAM_VAL("val"), 
		PATCH_PARAMS_LIST("params"), 
		
		PREF_MIDI_CHANNEL("midichannel"), 
		PREF_PITCH_BEND_RANGE("pitchbendrange"), 
		PREF_PITCH_BEND_FIX("pitchbendfix"), 
		PREF_POLYPHONY_VOICES("polyphonyvoices"),
		PREF_MIDI_CC_MAPPING_LIST("midiccmapping"),
		PREF_AUDIO_BUFFER_SIZE("audiobuffersize"),
		PREF_TRANSFER_PERFORMANCE_DATA("transferperformancedata"),
		PREF_LIMITER_ENABLED("limiterenabled"),
		PREF_MIDI_CC_SELECT("midi_cc_select"),
		PREF_MIDI_CC_VALUE("midi_cc_value"),
		
		UI_EXISTINGPATCHES("existingPatches"), 
		UI_CATEGORIES("categories"), 
		UI_SELECTED_CATEGORY("selectedcategory"), 
		UI_USER_PATCH_LIST("user"), 
		UI_FACTORY_PATCH_LIST("factory"), 
		UI_PATCH_ID("id"),
		UI_ACTION("action"),
		UI_ACTION_DELETE("delete"),
		UI_OVERWRITE_ID("overwrite");
		
		private String key;
		private K(String key) {
			this.key = key;
		}
		
		public String key() {
			return key;
		}
	}
	
	
	public static void main(String[] args) {
		String filepath = "./src/main/resources/factory-patches.json";
		try {
			String data = IOUtils.toString(new FileInputStream(filepath));
			JSONArray array = new JSONArray(data);
			JSONArray out = new JSONArray();
			for (int i=0;i<array.length();i++) {
				JSONObject patch = array.getJSONObject(i);
				JSONArray params = patch.getJSONArray(K.PATCH_PARAMS_LIST.key());
				JSONArray paramsOut = new JSONArray();
				for (int j=0;j<params.length();++j) {
					JSONObject param = params.getJSONObject(j);
					param.remove("description");
					param.remove("oscmsg");
					paramsOut.put(param);
				}
				patch.put(K.PATCH_PARAMS_LIST.key(), paramsOut);
				out.put(patch);
			}
			FileUtils.write(new File(filepath+"~"), out.toString(2), "utf-8");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
}
