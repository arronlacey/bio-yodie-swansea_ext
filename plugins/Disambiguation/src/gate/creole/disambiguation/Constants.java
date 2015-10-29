package gate.creole.disambiguation;

public class Constants {

	/**
	 * Trying to get all these variable names into one place only
	 * --don't want to bother the user with uninteresting PR parameters.
	 */
	//Some things we decided to fix
	public static String lookupType = "Lookup";
	public static String lookupListType = "LookupList";
	public static String outputSet = "Shef";
	public static String outputType = "Mention";
	public static String inst = "inst";
	public static String label = "label";
	public static String confidence = "LF_confidence";
	public static String type = "type";
	
	//Twitter expansion feature names
	public static String hashType = "TwitterExpanderHashtag";
	public static String urlType = "TwitterExpanderURL";
	public static String idType = "TwitterExpanderUserID";
	public static String hash = "Hashtag";
	public static String url = "URL";
	public static String id = "UserID";
	public static String twExpOrigTexSzDocFt = "TwitterExpanderOriginalTextSize";
	
	//name of the document feature to contain the revised coref
	public static String yodieCorefType = "YodieCoref";
	
	//For working only on key mentions (e.g. TAC corpus)
	public static String tacSwitch = "keyOverlapsOnly"; //This should match the doc feat in config
	public static String key = "Key";
	public static String mentionType = "Mention";
	
	//Not so sure where this came from, but it's used in Entity
	public static String mentionId = "mentionId";
}
