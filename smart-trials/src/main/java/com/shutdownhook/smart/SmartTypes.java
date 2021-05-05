/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

package com.shutdownhook.smart;

import java.lang.Comparable;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
	
public class SmartTypes
{
	// +---------+
	// | Patient |
	// +---------+

	// http://hl7.org/fhir/patient.html
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
			Patient p = gson.fromJson(json, Patient.class);
			if (p.identifier != null) Collections.sort(p.identifier);
			if (p.name != null) Collections.sort(p.name);
			if (p.telecom != null) Collections.sort(p.telecom);
			if (p.address != null) Collections.sort(p.address);
			return(p);
		}

		public Address bestAddress() {
			
			if (address == null || address.size() == 0) return(null);

			// walk looking for one in a valid period
			Instant now = Instant.now();
			for (Address a : address) {
				if (a.period == null || a.period.current()) return(a);
			}

			return(null);
		}

		public HumanName bestName() {
			
			if (name == null || name.size() == 0) return(null);

			// walk looking for one in a valid period
			Instant now = Instant.now();
			for (HumanName h : name) {
				if (h.period == null || h.period.current()) return(h);
			}

			return(null);
		}
	}

	// +-----------+
	// | Condition |
	// +-----------+

	// http://hl7.org/fhir/condition.html
	public static class Condition
	{
		public String id;
		public List<Identifier> identifier;
		public ClinicalStatusCode clinicalStatus;
		public VerificationStatusCode verificationStatus;
		public ConditionCategoryCodes category;
		public CodeableConcept code;
		
		public LocalDate onsetDateTime;
		public LocalDate dateRecorded; // DSTU2
		public LocalDate recordedDate; // R4

		public static Condition fromJson(String json) {
			Condition c = gson.fromJson(json, Condition.class);
			if (c.identifier != null) Collections.sort(c.identifier);
			return(c);
		}

		public static Condition fromJsonElement(JsonElement json) {
			Condition c = gson.fromJson(json, Condition.class);
			if (c.identifier != null) Collections.sort(c.identifier);
			return(c);
		}

		public boolean validAndActive() {
			return((clinicalStatus == null ||
					clinicalStatus.equals(ClinicalStatusCode.active) ||
					clinicalStatus.equals(ClinicalStatusCode.recurrence) ||
					clinicalStatus.equals(ClinicalStatusCode.relapse))
				   &&
				   (verificationStatus == null ||
					verificationStatus.equals(VerificationStatusCode.confirmed)));
		}

		public LocalDate bestGuessOnset() {
			// could try to deal with other versions of onset here
			if (onsetDateTime != null) return(onsetDateTime);
			if (recordedDate != null) return(recordedDate);
			if (dateRecorded != null) return(dateRecorded);
			return(null);
		}
	}

	// +------------+
	// | Primitives |
	// +------------+

	// http://hl7.org/fhir/datatypes.html#Address
	public static class Address implements Comparable<Address>
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

		public int compareTo(Address a) {
			int i = compareEnums(use, a.use); if (i != 0) return(i);
			return(compareEnums(type, a.type));
		}
	}

	// http://hl7.org/fhir/datatypes.html#ContactPoint
	public static class ContactPoint implements Comparable<ContactPoint>
	{
		public ContactPointSystem system;
		public String value;
		public ContactPointUse use;
		public Integer rank;
		public Period period;

		public int compareTo(ContactPoint c) {
			return(compareEnums(use, c.use));
		}
	}

	// http://hl7.org/fhir/datatypes.html#HumanName
	public static class HumanName implements Comparable<HumanName>
	{
		public HumanNameUse use;
		public String text;
		public String family; // array in R2, string in R4 ... consolidate
		public List<String> given; // leave this as an array to distinct first/middle
		public String prefix; // array, we just consolidate
		public String suffix; // array, we just consolidate
		public Period period;

		public String firstName() {
			return(given == null || given.size() == 0 ? null : given.get(0));
		}

		public String displayName() {

			if (text != null) return(text);
			
			StringBuilder sb = new StringBuilder();
			if (prefix != null) sb.append(prefix);

			if (given != null) {
				for (String name : given) {
					if (sb.length() > 0) sb.append(" ");
					sb.append(name);
				}
			}

			if (family != null) {
				if (sb.length() > 0) sb.append(" ");
				sb.append(family);
			}

			if (suffix != null) {
				if (sb.length() > 0) sb.append(" ");
				sb.append(suffix);
			}

			return(sb.toString());
		}
		
		public int compareTo(HumanName h) {
			return(compareEnums(use, h.use));
		}
	}

	// http://hl7.org/fhir/datatypes.html#Identifier
	public static class Identifier implements Comparable<Identifier>
	{
		public IdentifierUse use;
		public CodeableConcept type;
		public String system;
		public String value;
		public Period period;

		public int compareTo(Identifier i) {
			return(compareEnums(use, i.use));
		}
	}

	// http://hl7.org/fhir/datatypes.html#CodeableConcept
	public static class CodeableConcept
	{
		public List<Coding> coding;
		public String text;
	}

	// http://hl7.org/fhir/datatypes.html#Coding
	public static class Coding
	{
		public String system;
		public String version;
		public String code;
		public String display;
		public Boolean userSelected;
	}

	// http://hl7.org/fhir/datatypes.html#Period
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

	// this exists so that we can trigger the hugely and unnecessarily
	// complicated world of category codes betwen DSTU2 and R4
	public static class ConditionCategoryCodes extends HashSet<ConditionCategoryCode> { }

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

	// combo of:
	// (DSTU2) http://hl7.org/fhir/DSTU2/valueset-condition-category.html
	// (R4) http://hl7.org/fhir/valueset-condition-category.html
	// + one we seem to get from cerner that isn't in either one!
	public static enum ConditionCategoryCode
	{
		problem_list_item,
		problem,
		encounter_diagnosis,
		diagnosis,
		complaint,
		symptom,
		finding
	}

	// http://hl7.org/fhir/valueset-administrative-gender.html
	public static enum Gender
	{
		male,
		female,
		other,
		unknown
	}

	// http://hl7.org/fhir/valueset-contact-point-system.html
	public static enum ContactPointSystem
	{
		phone,
		email,
		fax,
		pager,
		url,
		sms,
		other
	}

	// +-------------------------+
	// | Enums (with Preference) |
	// +-------------------------+

	// Where meaningful, these are ordered so that the "best" or preferred values
	// appear first. This makes it easy for us to rely on the first value in a 
	// sorted list as the best, modulo issues like expiration by period, etc.
	// which are still on the user to figure out 

	// http://hl7.org/fhir/valueset-identifier-use.html
	public static enum IdentifierUse
	{
		usual,
		official,
		temp,
		secondary,
		old
	}

	// http://hl7.org/fhir/valueset-name-use.html
	public static enum HumanNameUse
	{
		usual,
		official,
		temp,
		nickname,
		anonymous,
		maiden,
		old
	}

	// http://hl7.org/fhir/valueset-contact-point-use.html
	public static enum ContactPointUse
	{
		mobile,
		home,
		work,
		temp,
		old
	}

	// http://hl7.org/fhir/valueset-address-use.html
	public static enum AddressUse
	{
		home,
		work,
		billing,
		temp,
		old
	}

	// http://hl7.org/fhir/valueset-address-type.html
	public static enum AddressType
	{
		physical,
		postal,
		both
	}

	private static int compareEnums(Enum e1, Enum e2) {
		if (e1 == null && e2 != null) return(-1); // nulls sort first
		if (e1 == null && e2 == null) return(0);
		if (e1 != null && e2 == null) return(1);
		return(e1.compareTo(e2));
	}

	// +------------+
	// | Converters |
	// +------------+

	public static class CccDeserializer implements JsonDeserializer<ConditionCategoryCodes>
	{
		public ConditionCategoryCodes deserialize(JsonElement json, Type typeOfT,
								  JsonDeserializationContext context)
			throws JsonParseException {

			ConditionCategoryCodes codes = new ConditionCategoryCodes();
			Gson gson = new Gson();

			if (json.isJsonArray()) {
				// r4
				JsonArray arr = (JsonArray) json;
				for (int i = 0; i < arr.size(); ++i) {
					JsonObject j = arr.get(i).getAsJsonObject();
					CodeableConcept c = gson.fromJson(j, CodeableConcept.class);
					addCode(codes, c.coding.get(0).code);
				}
			}
			else {
				// dstu2
				CodeableConcept c = gson.fromJson(json, CodeableConcept.class);
				addCode(codes, c.coding.get(0).code);
			}

			return(codes);
		}

		private void addCode(ConditionCategoryCodes codes, String input) {
			try {
				codes.add(ConditionCategoryCode.valueOf(input.replace("-", "_")));
			}
			catch (Exception e) {
				// ignore it
			}
		}
	}
	
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

	public static class CscDeserializer implements JsonDeserializer<ClinicalStatusCode>
	{
		public ClinicalStatusCode deserialize(JsonElement json, Type typeOfT,
										  JsonDeserializationContext context)
			throws JsonParseException {

			String codeStr = null;
			
			if (json.isJsonObject()) {
				CodeableConcept concept = new Gson().fromJson(json, CodeableConcept.class);
				codeStr = concept.coding.get(0).code;
			}
			else {
				codeStr = json.getAsString();
			}

			return(ClinicalStatusCode.valueOf(codeStr));
		}
	}
	
	public static class VscDeserializer implements JsonDeserializer<VerificationStatusCode>
	{
		public VerificationStatusCode deserialize(JsonElement json, Type typeOfT,
												  JsonDeserializationContext context)
			throws JsonParseException {

			String codeStr = null;
			
			if (json.isJsonObject()) {
				CodeableConcept concept = new Gson().fromJson(json, CodeableConcept.class);
				codeStr = concept.coding.get(0).code;
			}
			else {
				codeStr = json.getAsString();
			}

			return(VerificationStatusCode.valueOf(codeStr.replace("-", "_")));
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
			int year = (fields.length >= 1 ? Integer.parseInt(fields[0]) : 1);
			int month = (fields.length >= 2 ? Integer.parseInt(fields[1]) : 1);
			int day = (fields.length >= 3 ? Integer.parseInt(fields[2]) : 1);
			return(LocalDate.of(year, month, day));
		}
	}

	private static Gson gson = new GsonBuilder()
		.setPrettyPrinting()
		.registerTypeAdapter(String.class, new LaxStringDeserializer())
		.registerTypeAdapter(OffsetDateTime.class, new OdtDeserializer())
		.registerTypeAdapter(LocalDate.class, new LdDeserializer())
		.registerTypeAdapter(ClinicalStatusCode.class, new CscDeserializer())
		.registerTypeAdapter(VerificationStatusCode.class, new VscDeserializer())
		.registerTypeAdapter(ConditionCategoryCodes.class, new CccDeserializer())
		.create();
}

