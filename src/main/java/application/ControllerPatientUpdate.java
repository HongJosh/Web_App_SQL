package application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import view.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/*
 * Controller class for patient interactions.
 *   register as a new patient.
 *   update patient profile.
 */
@Controller
public class ControllerPatientUpdate {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	/*
	 *  Display patient profile for patient id.
	 */
	@GetMapping("/patient/edit/{id}")
	public String getUpdateForm(@PathVariable int id, Model model) {

		PatientView pv = new PatientView();
		// TODO search for patient by id
		//  if not found, return to home page using return "index"; 
		//  else create PatientView and add to model.
		pv.setId(id);
		try (Connection con = getConnection();) {

			PreparedStatement ps = con.prepareStatement("select first_name, last_name, birthdate, street, city, state, zipcode, doctor_id from patient where id = ?");
			ps.setInt(1, id);

			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				pv.setFirst_name(rs.getString(1));
				pv.setLast_name(rs.getString(2));
				pv.setBirthdate(rs.getString(3));
				pv.setStreet(rs.getString(4));
				pv.setCity(rs.getString(5));
				pv.setState(rs.getString(6));
				pv.setZipcode(rs.getString(7));

				// Get primary physician last name from doctor_id
				int doctor_id = rs.getInt(8);
				ps = con.prepareStatement("select last_name from doctor where id = ?");
				ps.setInt(1, doctor_id);
				rs = ps.executeQuery();
				if (rs.next()) {
					pv.setPrimaryName(rs.getString(1));
				}

				model.addAttribute("patient", pv);
				return "patient_edit";
			} else {
				model.addAttribute("message", "Patient not found");
				model.addAttribute("patient", pv);
				return "patient_get";
			}

		} catch	(SQLException e) {
			System.out.println("SQL error in getUpdateForm " + e.getMessage());
			model.addAttribute("message", "SQL Error. "+e.getMessage());
			model.addAttribute("patient", pv);
			return "patient_get";
		}
}


	/*
	 * Process changes from patient_edit form
	 *  Primary doctor, street, city, state, zip can be changed
	 *  ssn, patient id, name, birthdate, ssn are read only in template.
	 */
	@PostMapping("/patient/edit")
	public String updatePatient(PatientView p, Model model) {

		try (Connection con = getConnection();) {
			// validate doctor last name
			int doctor_id = 0;

			PreparedStatement ps = con.prepareStatement("select id from doctor where last_name = ?");
			// Get last name for doctor
			ps.setString(1, p.getPrimaryName());
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				// Doctor exists
				// Get doctor_id for corresponding last name
				doctor_id = rs.getInt(1);

				// TODO update patient profile data in database
				ps = con.prepareStatement("update patient set street=?, city=?, state=?, zipcode=?, doctor_id=? where id=?");
				ps.setString(1, p.getStreet());
				ps.setString(2, p.getCity());
				ps.setString(3, p.getState());
				ps.setString(4, p.getZipcode());
				ps.setInt(5, doctor_id);
				ps.setInt(6, p.getId());

				// rc is row count from executeUpdate
				// should be 1
				int rc = ps.executeUpdate();
				if (rc == 1) {
					model.addAttribute("message", "Update Successful");
					model.addAttribute("patient", p);
					return "patient_show";
				} else {
					model.addAttribute("message", "Error: Update was not successful");
					model.addAttribute("patient", p);
					return "patient_edit";
				}
			} else {
				model.addAttribute("message", "Error: Update was not successful (physician not found)");
				model.addAttribute("patient", p);
				return "patient_edit";
			}

		} catch (SQLException e) {
			System.out.println("SQL error in UpdatePatient " + e.getMessage());
			model.addAttribute("message", "SQL Error. "+e.getMessage());
			model.addAttribute("patient", p);
			return "patient_edit";
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
