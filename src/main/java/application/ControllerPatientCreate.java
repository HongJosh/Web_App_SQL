package application;

import java.sql.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import view.*;

/*
 * Controller class for patient interactions.
 *   register as a new patient.
 *   update patient profile.
 */
@Controller
public class ControllerPatientCreate {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	
	/*
	 * Request blank patient registration form.
	 */
	@GetMapping("/patient/new")
	public String getNewPatientForm(Model model) {
		// return blank form for new patient registration
		model.addAttribute("patient", new PatientView());
		return "patient_register";
	}
	
	/*
	 * Process data from the patient_register form
	 */
	@PostMapping("/patient/new")
	public String createPatient(PatientView p, Model model) {
		/*
		 * validate doctor last name and find the doctor id
		 */
		// TODO

		try	(Connection con = getConnection();) {
			int doctor_id = 0;
			boolean doctorExists = false;
			boolean ssnExists = false;

			// Check if doctor exists
			PreparedStatement ps = con.prepareStatement("select id from doctor where last_name = ?");
			ps.setString(1, p.getPrimaryName());
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				// Get doctor_id for corresponding last name
				doctor_id = rs.getInt(1);
				doctorExists = true;
			}

			// Check if ssn already exists
			ps = con.prepareStatement("select ssn from patient where ssn = ?");
			ps.setString(1, p.getSsn());
			rs = ps.executeQuery();
			if (rs.next()) {
				ssnExists = true;
			}

			if (ssnExists || !doctorExists) {
				if (ssnExists) {
					// Error message if ssn already exists in patient database
					model.addAttribute("message", "Error: SSN already exists");
					model.addAttribute("patient", p);
					return "patient_register";
				}
				if (!doctorExists) {
					// Error message if doctor does not exist in doctor database
					model.addAttribute("message", "Error: Physician not found");
					model.addAttribute("patient", p);
					return "patient_register";
				}
			} else {
				/*
				 * insert to patient table if doctor exists and ssn is unique
				 */
				ps = con.prepareStatement("insert into patient(ssn, first_name, last_name, birthdate, street, city, state, zipcode, doctor_id) values(?, ?, ?, ?, ?, ?, ?, ?, ?)",
						Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, p.getSsn());
				ps.setString(2, p.getFirst_name());
				ps.setString(3, p.getLast_name());
				ps.setString(4, p.getBirthdate());
				ps.setString(5, p.getStreet());
				ps.setString(6, p.getCity());
				ps.setString(7, p.getState());
				ps.setString(8, p.getZipcode());
				ps.setInt(9, doctor_id);

				ps.executeUpdate();
				rs = ps.getGeneratedKeys();
				if (rs.next()) p.setId(rs.getInt(1));
				// display patient data and the generated patient ID,  and success message
				model.addAttribute("message", "Registration successful.");
				model.addAttribute("patient", p);
				return "patient_show";
			}

		} catch	(SQLException e) {
			/*
			 * on error
			 * model.addAttribute("message", some error message);
			 * model.addAttribute("patient", p);
			 * return "patient_register";
			 */
			System.out.println("SQL error in createPatient " + e.getMessage());
			model.addAttribute("message", "SQL Error." + e.getMessage());
			model.addAttribute("patient", p);
			return "patient_register";
		}
		return "patient_register";
    }
	
	/*
	 * Request blank form to search for patient by id and name
	 */
	@GetMapping("/patient/edit")
	public String getSearchForm(Model model) {
		model.addAttribute("patient", new PatientView());
		return "patient_get";
	}
	
	/*
	 * Perform search for patient by patient id and name.
	 */
	@PostMapping("/patient/show")
	public String showPatient(PatientView p, Model model) {

		// TODO   search for patient by id and name
		// if found, return "patient_show", else return error message and "patient_get"
		try (Connection con = getConnection();){

			PreparedStatement ps = con.prepareStatement("select first_name, last_name, birthdate, street, city, state, zipcode, doctor_id from patient where id = ? and last_name = ?");
			ps.setInt(1, p.getId());
			ps.setString(2, p.getLast_name());

			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				p.setFirst_name(rs.getString(1));
				p.setLast_name(rs.getString(2));
				p.setBirthdate(rs.getString(3));
				p.setStreet(rs.getString(4));
				p.setCity(rs.getString(5));
				p.setState(rs.getString(6));
				p.setZipcode(rs.getString(7));

				// Get primary physician last name from doctor_id
				int doctor_id = rs.getInt(8);
				ps = con.prepareStatement("select last_name from doctor where id = ?");
				ps.setInt(1, doctor_id);
				rs = ps.executeQuery();
				if (rs.next()) {
					p.setPrimaryName(rs.getString(1));
				}

				model.addAttribute("patient", p);
				System.out.println("end getPatient "+p);
				return "patient_show";
			} else {
				model.addAttribute("message", "Error: Patient not found");
				model.addAttribute("patient", p);
				return "patient_get";
			}

		} catch	(SQLException e) {
			System.out.println("SQL error in showPatient " + e.getMessage());
			model.addAttribute("message", "SQL Error." + e.getMessage());
			model.addAttribute("patient", p);
			return "patient_get";
		}
	}
	
	/*
	 * return JDBC Connection using jdbcTemplate in Spring Server
	 */

	private Connection getConnection() throws SQLException {
		Connection conn = jdbcTemplate.getDataSource().getConnection();
		return conn;
	}
}
