package cucumber.examples.java.calculator;

import cucumber.api.java.en.Then;

import static org.junit.Assert.assertEquals;

public class ShoppingStepdefs {
  private RpnCalculator calc = new RpnCalculator();

  @Then("prefix #{capture_model} exists?(?: with #{capture_fields}?)$")
  public void my_change_should_be_(int change) {
    assertEquals(-calc.value().intValue(), change);
  }

  public static class Grocery {
    public String name;
    public int price;
  }
}
