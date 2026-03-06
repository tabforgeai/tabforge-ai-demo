package services;

import jakarta.ejb.Stateful;

@Stateful
public class UserService {
	public String findUser(String userId) {
		return "User " + userId + ": John Doe";
	}

	public String deleteUser(String userId) {
		return "User " + userId + " has been deleted";
	}

	public String getUserAdrres(String userId) {
		return "The Main Street 10";
	}

}
