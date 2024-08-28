package application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import view.*;

import static java.sql.DriverManager.getConnection;

@Controller
public class ControllerPrescriptionCreate {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/*
	 * Doctor requests blank form for new prescription.
	 */
	@GetMapping("/prescription/new")
	public String getPrescriptionForm(Model model) {
		model.addAttribute("prescription", new PrescriptionView());
		return "prescription_create";
	}

	// process data entered on prescription_create form
	@PostMapping("/prescription")
	public String createPrescription(PrescriptionView p, Model model) {

		System.out.println("createPrescription " + p);

		try {
			int doctorId = -1;
			int patientId = -1;
			int drugId = -1;

			// Get doctor ID
			try (Connection con = getConnection()) {
				PreparedStatement ps = con.prepareStatement("select id from doctor where last_name = ? and first_name = ?");
				ps.setString(1, p.getDoctorLastName());
				ps.setString(2, p.getDoctorFirstName());
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					doctorId = rs.getInt("id");
				} else {
					model.addAttribute("message", "Error: Doctor not found.");
					model.addAttribute("prescription", p);
					return "prescription_create";
				}
			}

			// Get patient ID
			try (Connection conn = getConnection()) {
				PreparedStatement ps = conn.prepareStatement("select patient_id from patient where last_name = ? and first_name = ?");
				ps.setString(1, p.getPatientLastName());
				ps.setString(2, p.getPatientFirstName());
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					patientId = rs.getInt("patient_id");
				} else {
					model.addAttribute("message", "Error: Patient not found.");
					model.addAttribute("prescription", p);
					return "prescription_create";
				}
			}

			if (doctorId != p.getDoctor_id()) {
				model.addAttribute("message", "Error: Doctor ID is not correct.");
				model.addAttribute("prescription", p);
				return "prescription_create";
			}

			if (patientId != p.getPatient_id()) {
				model.addAttribute("message", "Error: Patient ID is not correct.");
				model.addAttribute("prescription", p);
				return "prescription_create";
			}

			// Get drug ID
			try (Connection conn = getConnection()) {
				PreparedStatement ps = conn.prepareStatement("select drug_id from drug where drugname = ?");
				ps.setString(1, p.getDrugName());
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					drugId = rs.getInt("drug_id");
				} else {
					model.addAttribute("message", "Error: Drug not found.");
					model.addAttribute("prescription", p);
					return "prescription_create";
				}
			}

			// Insert prescription into the database and prescriptionfill
			try (Connection conn = getConnection()) {
				PreparedStatement ps = conn.prepareStatement("insert into prescription (doctor_id, patient_id, drug_id, quantity, refills) values (?, ?, ?, ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS);
				ps.setInt(1, doctorId);
				ps.setInt(2, patientId);
				ps.setInt(3, drugId);
				ps.setInt(4, p.getQuantity());
				ps.setInt(5, p.getRefills());

				int affectedRows = ps.executeUpdate();

				if (affectedRows == 0) {
					throw new SQLException("Creating prescription failed, no rows affected.");
				}

				ResultSet generatedKeys = ps.getGeneratedKeys();
				if (generatedKeys.next()) {
					int rxid = generatedKeys.getInt(1);
					p.setRxid(rxid);

					PreparedStatement psPrescriptionFill = conn.prepareStatement("insert into prescriptionfill (prescriptionfillid, rx_id, date_created, cost) values (?, ?, ?, ?)");
					psPrescriptionFill.setInt(1, 1);
					psPrescriptionFill.setInt(2, rxid);
					psPrescriptionFill.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
					psPrescriptionFill.setString(4, p.getCost());

					int affectedRowsPrescriptionFill = psPrescriptionFill.executeUpdate();

					if (affectedRowsPrescriptionFill == 0) {
						throw new SQLException("Creating prescriptionfill failed, no rows affected.");
					}
				} else {
					throw new SQLException("Creating prescription failed, no ID obtained.");
				}

				model.addAttribute("message", "Prescription created.");
				model.addAttribute("prescription", p);
				return "prescription_show";
			} catch (SQLException e) {
				model.addAttribute("message", "Error: " + e.getMessage());
				model.addAttribute("prescription", p);
				return "prescription_create";
			}
		} catch (SQLException e) {
			model.addAttribute("message", "Error: " + e.getMessage());
			model.addAttribute("prescription", p);
			return "prescription_create";
		}
	}

	private Connection getConnection() throws SQLException {
		Connection conn = jdbcTemplate.getDataSource().getConnection();
		return conn;
	}

}
