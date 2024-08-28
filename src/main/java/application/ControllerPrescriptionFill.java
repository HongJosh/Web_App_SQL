package application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import view.*;

import static java.sql.DriverManager.getConnection;

@Controller
public class ControllerPrescriptionFill {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/*
	 * Patient requests form to fill prescription.
	 */
	@GetMapping("/prescription/fill")
	public String getfillForm(Model model) {
		model.addAttribute("prescription", new PrescriptionView());
		return "prescription_fill";
	}

	// process data from prescription_fill form
	@PostMapping("/prescription/fill")
	public String processFillForm(PrescriptionView p, Model model) {
		try {
			int pharmacyId = -1;
			int patientId = -1;

			// Validate pharmacy name and address, get pharmacy id and phone
			try (Connection con = getConnection()) {
				PreparedStatement psPharmacy = con.prepareStatement("select pharmacy_id, phonenumber from pharmacy where name = ? and address = ?");
				psPharmacy.setString(1, p.getPharmacyName());
				psPharmacy.setString(2, p.getPharmacyAddress());
				ResultSet rsPharmacy = psPharmacy.executeQuery();
				if (rsPharmacy.next()) {
					pharmacyId = rsPharmacy.getInt("pharmacy_id");
					p.setPharmacyPhone(rsPharmacy.getString("phonenumber"));
				} else {
					model.addAttribute("message", "Error: Pharmacy not found.");
					model.addAttribute("prescription", p);
					return "prescription_fill";
				}
			}

			// Find patient information and prescription
			try (Connection conn = getConnection()) {
				PreparedStatement psPatient = conn.prepareStatement("select p.patient_id, pr.* from patient p inner join prescription pr on p.patient_id = pr.patient_id where pr.rx_id = ? and p.last_name = ?");
				psPatient.setInt(1, p.getRxid());
				psPatient.setString(2, p.getPatientLastName());
				ResultSet rsPatient = psPatient.executeQuery();
				if (rsPatient.next()) {
					patientId = rsPatient.getInt("patient_id");
					p.setRxid(rsPatient.getInt("rx_id"));
					p.setQuantity(rsPatient.getInt("quantity"));
					p.setRefills(rsPatient.getInt("refills"));
				} else {
					model.addAttribute("message", "Error: Prescription not found for this specific patient.");
					model.addAttribute("prescription", p);
					return "prescription_fill";
				}
			}

			// Prescription fills and refills
			if (p.getRefills() > 0) {
				int newRefills = p.getRefills() - 1;
				try (Connection conn = getConnection()) {
					PreparedStatement psUpdateRefills = conn.prepareStatement("update prescription set refills = ? where rx_id = ?");
					psUpdateRefills.setInt(1, newRefills);
					psUpdateRefills.setInt(2, p.getRxid());
					psUpdateRefills.executeUpdate();
				} catch (SQLException e) {
					model.addAttribute("message", "Error: " + e.getMessage());
					model.addAttribute("prescription", p);
					return "prescription_fill";
				}
			} else {
				try (Connection conn = getConnection()) {
					PreparedStatement psUpdateRefills = conn.prepareStatement("update prescription set refills = ?, rx_id = ? where rx_id = ?");
					psUpdateRefills.setInt(1, 0);
					psUpdateRefills.setInt(2, 0);
					psUpdateRefills.setInt(3, p.getRxid());
					psUpdateRefills.executeUpdate();
				} catch (SQLException e) {
					model.addAttribute("message", "Error: " + e.getMessage());
					model.addAttribute("prescription", p);
					return "prescription_fill";
				}

				model.addAttribute("message", "Error: This prescription has no refills remaining and is no longer valid.");
				model.addAttribute("prescription", p);
				return "prescription_fill";
			}

			// Calculate the cost of the prescription
			double cost = 0.00;
			try (Connection conn = getConnection()) {
				PreparedStatement ps = conn.prepareStatement("select cost, unitamount from drugcost where drug_id = ?");
				ps.setInt(1, p.getRxid());
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					double unitAmount = rs.getDouble("cost");
					int quantity = p.getQuantity();
					cost = unitAmount * quantity;
					String formattedCost = String.format("%.2f", cost);
					cost = Double.parseDouble(formattedCost);
				} else {
					model.addAttribute("message", "Error: Cost not found for the drug.");
					model.addAttribute("prescription", p);
					return "prescription_fill";
				}
			}

			// Record the prescriptionfill table  details
			try (Connection conn = getConnection()) {
				PreparedStatement psRecordFill = conn.prepareStatement("insert into prescriptionfill (rx_id, pharmacy_id, date_created, cost) values (?, ?, ?, ?)");
				psRecordFill.setInt(1, p.getRxid());
				psRecordFill.setInt(2, pharmacyId);
				psRecordFill.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
				psRecordFill.setDouble(4, cost);
				psRecordFill.executeUpdate();
			}

			// Update prescription in the database
			try (Connection conn = getConnection()) {
				PreparedStatement psUpdate = conn.prepareStatement("update prescription set refills = ? where rx_id = ?");
				psUpdate.setInt(1, p.getRefills());
				psUpdate.setInt(2, p.getRxid());
				int affectedRows = psUpdate.executeUpdate();
				if (affectedRows == 0) {
					throw new SQLException("Updating prescription failed, no rows affected.");
				}
			}

			// Retrieve and provide additional fields on prescription page
			try (Connection conn = getConnection()) {
				PreparedStatement ps = conn.prepareStatement("select pr.*, pt.first_name as patient_first_name, pt.last_name as patient_last_name, " +
						"dctr.first_name as doctor_first_name, dctr.last_name as doctor_last_name, drug.drugname " +
						"from prescription pr " +
						"join patient pt on pr.patient_id = pt.patient_id " +
						"join doctor dctr on pr.doctor_id = dctr.id " +
						"join drug on pr.drug_id = drug.drug_id " +
						"where rx_id = ?");
				ps.setInt(1, p.getRxid());
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					p.setPatient_id(rs.getInt("patient_id"));
					p.setPatientFirstName(rs.getString("patient_first_name"));
					p.setPatientLastName(rs.getString("patient_last_name"));
					p.setDoctor_id(rs.getInt("doctor_id"));
					p.setDoctorFirstName(rs.getString("doctor_first_name"));
					p.setDoctorLastName(rs.getString("doctor_last_name"));
					p.setDrugName(rs.getString("drugname"));

				} else {
					model.addAttribute("message", "Error: Prescription not found.");
					model.addAttribute("prescription", p);
					return "prescription_fill";
				}
			} catch (SQLException e) {
				model.addAttribute("message", "Error: " + e.getMessage());
				model.addAttribute("prescription", p);
				return "prescription_fill";
			}

			// Set prescription details and return prescription filled message
			try (Connection conn = getConnection()) {
				PreparedStatement ps = conn.prepareStatement("select pharmacy_id from pharmacy where name = ?");
				ps.setString(1, p.getPharmacyName());
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					pharmacyId = rs.getInt("pharmacy_id");
				} else {
					throw new SQLException("Pharmacy name not found: " + p.getPharmacyName());
				}
			} catch (SQLException e) {
				model.addAttribute("message", "Error: " + e.getMessage());
				model.addAttribute("prescription", p);
				return "prescription_fill";
			}

			p.setPharmacyID(pharmacyId);
			p.setDateFilled(LocalDate.now().toString());
			p.setCost(String.valueOf(cost));
			p.setPharmacyName(p.getPharmacyName());
			p.setPharmacyAddress(p.getPharmacyAddress());

			try (Connection conn = getConnection()) {
				PreparedStatement psUpdatePrescription = conn.prepareStatement("update prescription set pharmacy_id = ? where rx_id = ?");
				psUpdatePrescription.setInt(1, pharmacyId);
				psUpdatePrescription.setInt(2, p.getRxid());
				psUpdatePrescription.executeUpdate();
			}

			model.addAttribute("message", "Prescription filled.");
			model.addAttribute("prescription", p);
			return "prescription_show";
		} catch (SQLException e) {
			model.addAttribute("message", "Error: " + e.getMessage());
			model.addAttribute("prescription", p);
			return "prescription_fill";
		}
	}


	private Connection getConnection() throws SQLException {
		Connection conn = jdbcTemplate.getDataSource().getConnection();
		return conn;
	}

}