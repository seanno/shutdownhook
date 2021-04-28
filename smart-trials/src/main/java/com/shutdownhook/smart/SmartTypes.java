/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.smart;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
	
public class SmartTypes
{
	// +---------+
	// | Patient |
	// +---------+

	public static class Patient
	{
		public String id;
		public List<Identifier> identifier;
		public Boolean active;
		public List<HumanName> name;
		public List<ContactPoint> telecom;
		public Gender gender;
		public LocalDate birthDate;
		public List<Address> address;

		public static Patient fromJson(String json) {
			return(gson.fromJson(json, Patient.class));
		}

		public Address bestAddress() {
			
			if (address == null || address.size() == 0) {
				return(null);
			}

			// first sort so best are at the front
			Collections.sort(address, new Comparator<Address>() {
				public int compare(Address a1, Address a2) {
					if (a1 == null && a2 == null) return(0);
					if (a1 == null && a2 != null) return(-1);
					if (a1 != null && a2 == null) return(1);
					return(a1.use.ordinal() - a2.use.ordinal());
				}
			});

			// then walk looking for one in a valid period
			Instant now = Instant.now();
			for (Address a : address) {
				if (a.period == null) return(a);
				if (a.period.current()) return(a);
			}

			return(null);
		}
	}

	// +-----------+
	// | Condition |
	// +-----------+

	public static class Condition
	{
		public String id;
		public List<Identifier> identifier;
		public CodeableConcept clinicalStatus;
		public CodeableConcept verificationStatus;
		public CodeableConcept code;

		public static Condition fromJson(String json) {
			return(gson.fromJson(json, Condition.class));
		}

		public static Condition fromJsonElement(JsonElement json) {
			return(gson.fromJson(json, Condition.class));
		}

		public ClinicalStatusCode clinicalStatusCode() {
			if (clinicalStatus == null ||
				clinicalStatus.coding == null ||
				clinicalStatus.coding.get(0).code == null) {
				
				return(ClinicalStatusCode.active);
			}
			
			return(ClinicalStatusCode.valueOf(clinicalStatus.coding.get(0).code));
		}

		public VerificationStatusCode verificationStatusCode() {
			if (verificationStatus == null ||
				verificationStatus.coding == null ||
				verificationStatus.coding.get(0).code == null) {

				return(VerificationStatusCode.confirmed);
			}
			
			String val = verificationStatus.coding.get(0).code.replace("-", "_");
			return(VerificationStatusCode.valueOf(val));
		}

		public boolean validAndActive() {
			ClinicalStatusCode clinical = clinicalStatusCode();
			VerificationStatusCode verification = verificationStatusCode();

			return((clinical.equals(ClinicalStatusCode.active) ||
					clinical.equals(ClinicalStatusCode.recurrence) ||
					clinical.equals(ClinicalStatusCode.relapse))
				   &&
				   (verification.equals(VerificationStatusCode.confirmed)));
		}
	}

	// +------------+
	// | Primitives |
	// +------------+

	public static class Address
	{
		public AddressUse use;
		public AddressType type;
		public String text;
		public List<String> line;
		public String city;
		public String district;
		public String state;
		public String postalCode;
		public String country;
		public Period period;
	}

	public static class ContactPoint
	{
		public ContactPointSystem system;
		public String value;
		public ContactPointUse use;
		public Integer rank;
		public Period period;
	}
	
	public static class HumanName
	{
		public HumanNameUse use;
		public String text;
		public String family;
		public String given;
		public String prefix;
		public String suffix;
		public Period period;
	}

	public static class Identifier
	{
		public IdentifierUse use;
		public CodeableConcept type;
		public String system;
		public String value;
		public Period period;
	}
	
	public static class CodeableConcept
	{
		public List<Coding> coding;
		public String text;
	}

	public static class Coding
	{
		public String system;
		public String version;
		public String code;
		public String display;
		public Boolean userSelected;
	}

	public static class Period
	{
		public OffsetDateTime start; 
		public OffsetDateTime end;

		public boolean current() {
			Instant now = Instant.now();
			if (start != null && start.toInstant().isAfter(now)) return(false);
			if (end != null && end.toInstant().isBefore(now)) return(false);
			return(true);
		}
	}

	// +-------+
	// | Enums |
	// +-------+

	// https://www.hl7.org/fhir/valueset-condition-clinical.html
	public static enum ClinicalStatusCode
	{
		active,
		recurrence,
		relapse,
		inactive,
		remission,
		resolved
	}
	
	// https://www.hl7.org/fhir/valueset-condition-ver-status.html
	public static enum VerificationStatusCode
	{
		unconfirmed,
		provisional,
		differential,
		confirmed,
		refuted,
		entered_in_error
	}

	public static enum Gender
	{
		male,
		female,
		other,
		unknown
	}

	public static enum ContactPointSystem
	{
		phone,
		fax,
		email,
		pager,
		url,
		sms,
		other
	}

	public static enum ContactPointUse
	{
		home,
		work,
		temp,
		old,
		mobile
	}

	public static enum IdentifierUse
	{
		usual,
		official,
		temp,
		secondary,
		old
	}

	public static enum HumanNameUse
	{
		usual,
		official,
		temp,
		nickname,
		anonymous,
		old,
		maiden
	}

	// http://hl7.org/fhir/valueset-address-use.html
	// Ordered for sorting purposes (earlier is "more representative")
	public static enum AddressUse
	{
		home,
		work,
		billing,
		temp,
		old
	}

	public static enum AddressType
	{
		postal,
		physical,
		both
	}
	
	// +------------+
	// | Converters |
	// +------------+

	public static class LaxStringDeserializer implements JsonDeserializer<String>
	{
		public String deserialize(JsonElement json, Type typeOfT,
								  JsonDeserializationContext context)
			throws JsonParseException {

			if (!json.isJsonArray()) {
				return(json.getAsString());
			}

			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < ((JsonArray)json).size(); ++i) {
				if (sb.length() > 0) sb.append(" ");
				sb.append(((JsonArray)json).get(i).getAsString());
			}

			return(sb.toString());
		}
	}

	public static class OdtDeserializer implements JsonDeserializer<OffsetDateTime>
	{
		public OffsetDateTime deserialize(JsonElement json, Type typeOfT,
										  JsonDeserializationContext context)
			throws JsonParseException {

			return(OffsetDateTime.parse(json.getAsString()));
		}
	}

	public static class LdDeserializer implements JsonDeserializer<LocalDate>
	{
		public LocalDate deserialize(JsonElement json, Type typeOfT,
									 JsonDeserializationContext context)
			throws JsonParseException {

			// note we trim off a time part ... from what I read a fhir "date"
			// should never have a time component but reality disagrees.
			String[] fields = json.getAsString().split("T")[0].split("-");
			int year = (fields.length >= 1 ? Integer.parseInt(fields[0]) : 0);
			int month = (fields.length >= 2 ? Integer.parseInt(fields[1]) : 0);
			int day = (fields.length >= 3 ? Integer.parseInt(fields[2]) : 0);
			return(LocalDate.of(year, month, day));
		}
	}

	private static Gson gson = new GsonBuilder()
		.setPrettyPrinting()
		.registerTypeAdapter(String.class, new LaxStringDeserializer())
		.registerTypeAdapter(OffsetDateTime.class, new OdtDeserializer())
		.registerTypeAdapter(LocalDate.class, new LdDeserializer())
		.create();
}

