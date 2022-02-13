package dev.retrofit;

import retrofit2.Call;
import retrofit2.http.GET;

import java.util.Objects;

public interface TestService {
  @GET("/test")
  Call<User> testUser();

  public static class User {
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    private String name;

    public User(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      User user = (User) o;
      return Objects.equals(name, user.name);
    }
  }
}
