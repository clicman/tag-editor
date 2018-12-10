package cucumber.examples.java.calculator;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import static org.junit.Assert.assertEquals;

public class ShoppingStepdefs {
  private RpnCalculator calc = new RpnCalculator();

  @When("^I (will )?pay (\\d+)$")
  public void i_pay(int amount) {
    calc.push(amount);
    calc.push("-");
  }


  @When("^I (will )?buy (\\d+) things$")
  public void i_pay(int amount) {
    calc.push(amount);
    calc.push("-");
  }

  @Then("^my \"([^\"]*)\" should be (\\d+)$")
  public void my_change_should_be_(int change) {
    assertEquals(-calc.value().intValue(), change);
  }

  public static class Grocery {
    public String name;
    public int price;
  }
}
