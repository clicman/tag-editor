package cucumber.examples.java.calculator;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class ShoppingStepdefs {
  @And("^test \\\"([^\\\"]*)\\\"$")
  public void test(String arg1) throws Throwable {
  }

  @When("^I subtract (\\d+) from (\\d+)$")
  public void I_subtract_from(int arg1, int arg2) throws Throwable {
  }

  @Then("^the result is (\\d+)$")
  public void the_result_is(double expected) {
  }
}
