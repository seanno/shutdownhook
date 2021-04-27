/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.trials;

public class ClinicalTrialsTypes
{
	// +--------------+
	// | LegacyGender |
	// +--------------+
	
	public enum LegacyGender
	{
		M("Male"),
		F("Female"),
		O("Other"),
		U("Unknown");

		LegacyGender(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return(displayName);
		}

		public static LegacyGender find(String input) {

			for (LegacyGender lg : LegacyGender.values()) {
				if (lg.name().equalsIgnoreCase(input)) return(lg);
				if (lg.getDisplayName().equalsIgnoreCase(input)) return(lg);
			}

			return(null);
		}
	
		private String displayName;
	}

	// +---------------+
	// | StateProvince |
	// +---------------+
	
	public enum StateProvince
	{
		AL("Alabama"),
		AK("Alaska"),
		AB("Alberta"),
		AS("American Samoa"),
		AZ("Arizona"),
		AR("Arkansas"),
		AE("Armed Forces (AE)"),
		AA("Armed Forces Americas"),
		AP("Armed Forces Pacific"),
		BC("British Columbia"),
		CA("California"),
		CO("Colorado"),
		CT("Connecticut"),
		DE("Delaware"),
		DC("District Of Columbia"),
		FL("Florida"),
		GA("Georgia"),
		GU("Guam"),
		HI("Hawaii"),
		ID("Idaho"),
		IL("Illinois"),
		IN("Indiana"),
		IA("Iowa"),
		KS("Kansas"),
		KY("Kentucky"),
		LA("Louisiana"),
		ME("Maine"),
		MB("Manitoba"),
		MD("Maryland"),
		MA("Massachusetts"),
		MI("Michigan"),
		MN("Minnesota"),
		MS("Mississippi"),
		MO("Missouri"),
		MT("Montana"),
		NE("Nebraska"),
		NV("Nevada"),
		NB("New Brunswick"),
		NH("New Hampshire"),
		NJ("New Jersey"),
		NM("New Mexico"),
		NY("New York"),
		NF("Newfoundland"),
		NC("North Carolina"),
		ND("North Dakota"),
		NT("Northwest Territories"),
		NS("Nova Scotia"),
		NU("Nunavut"),
		OH("Ohio"),
		OK("Oklahoma"),
		ON("Ontario"),
		OR("Oregon"),
		PA("Pennsylvania"),
		PE("Prince Edward Island"),
		PR("Puerto Rico"),
		QC("Quebec"),
		RI("Rhode Island"),
		SK("Saskatchewan"),
		SC("South Carolina"),
		SD("South Dakota"),
		TN("Tennessee"),
		TX("Texas"),
		UT("Utah"),
		VT("Vermont"),
		VI("Virgin Islands"),
		VA("Virginia"),
		WA("Washington"),
		WV("West Virginia"),
		WI("Wisconsin"),
		WY("Wyoming"),
		YT("Yukon Territory");

		StateProvince(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return(displayName);
		}

		public static StateProvince find(String input) {

			for (StateProvince sp : StateProvince.values()) {
				if (sp.name().equalsIgnoreCase(input)) return(sp);
				if (sp.getDisplayName().equalsIgnoreCase(input)) return(sp);
			}

			return(null);
		}
	
		private String displayName;
	}

}

