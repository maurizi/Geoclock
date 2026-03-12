package maurizi.geoclock.utils;

import android.location.Address;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AddressFormatterTest {

	@Test
	public void shortAddress_withSubThoroughfareAndThoroughfare_returnsNumberAndStreet() {
		Address addr = new Address(Locale.US);
		addr.setThoroughfare("Main St");
		addr.setSubThoroughfare("123");
		assertEquals("123 Main St", AddressFormatter.shortAddress(addr));
	}

	@Test
	public void shortAddress_withThoroughfareOnly_returnsStreetName() {
		Address addr = new Address(Locale.US);
		addr.setThoroughfare("Broadway");
		assertEquals("Broadway", AddressFormatter.shortAddress(addr));
	}

	@Test
	public void shortAddress_noThoroughfare_withLocality_returnsLocality() {
		Address addr = new Address(Locale.US);
		addr.setLocality("San Francisco");
		assertEquals("San Francisco", AddressFormatter.shortAddress(addr));
	}

	@Test
	public void shortAddress_noThoroughfareNoLocality_withAddressLine_returnsAddressLine() {
		Address addr = new Address(Locale.US);
		addr.setAddressLine(0, "123 Main St, San Francisco, CA 94105");
		assertEquals("123 Main St, San Francisco, CA 94105", AddressFormatter.shortAddress(addr));
	}

	@Test
	public void shortAddress_noFields_returnsNull() {
		Address addr = new Address(Locale.US);
		assertNull(AddressFormatter.shortAddress(addr));
	}

	@Test
	public void shortAddress_thoroughfarePrioritizedOverLocality() {
		Address addr = new Address(Locale.US);
		addr.setThoroughfare("Elm St");
		addr.setLocality("Springfield");
		// Thoroughfare takes priority
		assertEquals("Elm St", AddressFormatter.shortAddress(addr));
	}
}
