/*
 * Created on Jan 10, 2018
 */
package com.leastlogic.moneydance.util;

import static com.infinitekind.moneydance.model.Account.AccountType.ASSET;
import static java.math.RoundingMode.HALF_EVEN;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

import com.infinitekind.moneydance.model.Account;
import com.infinitekind.moneydance.model.AccountBook;
import com.infinitekind.moneydance.model.AccountUtil;
import com.infinitekind.moneydance.model.AcctFilter;
import com.infinitekind.moneydance.model.CurrencySnapshot;
import com.infinitekind.moneydance.model.CurrencyType;

/**
 * Collection of common utility methods handy for Moneydance extensions.
 */
public class MdUtil {
	private static final double[] centMult = { 1, 10, 100, 1000, 10000 };

	/**
	 * @param security The Moneydance security
	 * @param latestSnapshot The last currency snapshot for the supplied security
	 * @return The price in latestSnapshot
	 */
	public static double validateCurrentUserRate(CurrencyType security,
			CurrencySnapshot latestSnapshot) {
		double price = convRateToPrice(latestSnapshot.getUserRate());
		double oldPrice = convRateToPrice(security.getUserRate());

		if (price != oldPrice) {
			security.setUserRate(latestSnapshot.getUserRate());
			DecimalFormat priceFmt = (DecimalFormat) NumberFormat.getCurrencyInstance();
			priceFmt.setMinimumFractionDigits(8);

			System.err.format("Changed security %s (%s) current price from %s to %s.%n",
				security.getName(), security.getTickerSymbol(), priceFmt.format(oldPrice),
				priceFmt.format(price));
		}

		return price;
	} // end validateCurrentUserRate(CurrencyType, CurrencySnapshot)

	/**
	 * @param rate The Moneydance currency rate for a security
	 * @return The security price rounded to the tenth place past the decimal point
	 */
	public static double convRateToPrice(double rate) {

		return roundPrice(1 / rate);
	} // end convRateToPrice(double)

	/**
	 * @param price The price
	 * @return Price rounded to the tenth place past the decimal point
	 */
	public static double roundPrice(double price) {
		BigDecimal bd = BigDecimal.valueOf(price);

		return bd.setScale(10, HALF_EVEN).doubleValue();
	} // end roundPrice(double)

	/**
	 * @param dateInt The numeric date value in decimal form YYYYMMDD
	 * @return The corresponding local date
	 */
	public static LocalDate convDateIntToLocal(int dateInt) {
		int year = dateInt / 10000;
		int month = (dateInt % 10000) / 100;
		int dayOfMonth = dateInt % 100;

		return LocalDate.of(year, month, dayOfMonth);
	} // end convDateIntToLocal(int)

	/**
	 * @param date The local date value
	 * @return The corresponding numeric date value in decimal form YYYYMMDD
	 */
	public static int convLocalToDateInt(LocalDate date) {
		int dateInt = date.getYear() * 10000
				+ date.getMonthValue() * 100
				+ date.getDayOfMonth();

		return dateInt;
	} // end convLocalToDateInt(LocalDate)

	/**
	 * @param security The Moneydance security
	 * @return The last currency snapshot for the supplied security
	 */
	public static CurrencySnapshot getLatestSnapshot(CurrencyType security) {
		List<CurrencySnapshot> snapShots = security.getSnapshots();

		return snapShots.get(snapShots.size() - 1);
	} // end getLatestSnapshot(CurrencyType)

	/**
	 * @param security The Moneydance security
	 * @param dateInt The desired date
	 * @return The currency snapshot for the supplied security on the specified date
	 */
	public static CurrencySnapshot getSnapshotForDate(CurrencyType security, int dateInt) {
		List<CurrencySnapshot> snapShots = security.getSnapshots();
		int index = snapShots.size();

		// start with the latest snapshot
		CurrencySnapshot candidate = snapShots.get(--index);
		validateCurrentUserRate(security, candidate);

		while (candidate.getDateInt() > dateInt && index > 0) {
			// examine the prior snapshot
			candidate = snapShots.get(--index);
		}

		return candidate;
	} // end getSnapshotForDate(CurrencyType, int)

	/**
	 * @param account The parent account
	 * @param securityName Security name
	 * @return The Moneydance security subaccount with the specified name
	 */
	public static Account getSubAccountByName(Account account, String securityName) {
		List<Account> subs = account.getSubAccounts(new AcctFilter() {

			public boolean matches(Account acct) {
				String acctName = acct.getAccountName();

				return acctName.equalsIgnoreCase(securityName);
			} // end matches(Account)

			public String format(Account acct) {

				return acct.getFullAccountName();
			} // end format(Account)
		}); // end new AcctFilter() {...}

		return subs == null || subs.isEmpty() ? null : subs.get(0);
	} // end getSubAccountByName(Account, String)

	/**
	 * @param account The root account
	 * @param accountNum Investment account number
	 * @return The Moneydance investment account with the specified number
	 */
	public static Account getSubAccountByInvestNumber(Account account, String accountNum) {
		List<Account> subs = account.getSubAccounts(new AcctFilter() {

			public boolean matches(Account acct) {
				String acctNum = acct.getInvestAccountNumber();

				return acctNum.equalsIgnoreCase(accountNum);
			} // end matches(Account)

			public String format(Account acct) {

				return acct.getFullAccountName();
			} // end format(Account)
		}); // end new AcctFilter() {...}

		return subs == null || subs.isEmpty() ? null : subs.get(0);
	} // end getSubAccountByInvestNumber(Account, String)

	/**
	 * @param account Moneydance account
	 * @return The current account balance
	 */
	public static double getCurrentBalance(Account account) {
		long centBalance;

		if (account.getAccountType() == ASSET) {
			centBalance = account.getRecursiveUserCurrentBalance();
		} else {
			centBalance = account.getUserCurrentBalance();
		}
		int decimalPlaces = account.getCurrencyType().getDecimalPlaces();

		return centBalance / centMult[decimalPlaces];
	} // end getCurrentBalance(Account)

	/**
	 * @param book The root account for all transactions
	 * @param account Moneydance account to obtain the balance for
	 * @param asOfDates The dates to obtain the balance for
	 * @return Account cent balances as of the end of each date in asOfDates
	 */
	private static long[] getCentBalancesAsOfDates(AccountBook book, Account account,
			int[] asOfDates) {
		long[] centBalances = AccountUtil.getBalancesAsOfDates(book, account, asOfDates);

		if (account.getAccountType() == ASSET) {
			// recurse to get subaccount balances
			Iterator<Account> accts = AccountUtil.getAccountIterator(account);

			while (accts.hasNext()) {
				Account subAcct = accts.next();

				if (subAcct != account) {
					long[] subBalances = getCentBalancesAsOfDates(book, subAcct, asOfDates);

					for (int i = 0; i < centBalances.length; ++i) {
						centBalances[i] += subBalances[i];
					}
				}
			}
		}

		return centBalances;
	} // end getCentBalancesAsOfDates(AccountBook, Account, int[])

	/**
	 * @param book The root account for all transactions
	 * @param account Moneydance account to obtain the balance for
	 * @param asOfDates The dates to obtain the balance for
	 * @return Account balances as of the end of each date in asOfDates
	 */
	public static double[] getBalancesAsOfDates(AccountBook book, Account account,
			int[] asOfDates) {
		long[] centBalances = getCentBalancesAsOfDates(book, account, asOfDates);
		double[] balances = new double[centBalances.length];
		int decimalPlaces = account.getCurrencyType().getDecimalPlaces();

		for (int i = 0; i < balances.length; ++i) {
			balances[i] = centBalances[i] / centMult[decimalPlaces];
		} // end for

		return balances;
	} // end getBalancesAsOfDates(AccountBook, Account, int[])

	/**
	 * @param baseBundleName The base name of the resource bundle, a fully qualified class name
	 * @param locale The locale for which a resource bundle is desired
	 * @return A resource bundle instance for the specified base bundle name
	 */
	public static ResourceBundle getMsgBundle(String baseBundleName, Locale locale) {
		ResourceBundle messageBundle;

		try {
			messageBundle = ResourceBundle.getBundle(baseBundleName, locale);
		} catch (Exception e) {
			System.err.format(locale, "Unable to load message bundle %s. %s%n", baseBundleName, e);

			messageBundle = new ResourceBundle() {
				protected Object handleGetObject(String key) {
					// just use the key since we have no message bundle
					return key;
				}

				public Enumeration<String> getKeys() {
					return null;
				}
			}; // end new ResourceBundle() {...}
		} // end catch

		return messageBundle;
	} // end getMsgBundle(String, Locale)

	/**
	 * @param propsFileName
	 * @param srcClass The class whose class loader will be used
	 * @return A properties instance with the specified file content
	 */
	public static Properties loadProps(String propsFileName, Class<?> srcClass)
			throws MduException {
		InputStream propsStream = srcClass.getClassLoader().getResourceAsStream(propsFileName);
		if (propsStream == null) {
			throw new MduException(null, "Unable to find properties %s on %s class path.",
				propsFileName, srcClass);
		}

		Properties props = new Properties();
		try {
			props.load(propsStream);
		} catch (Exception e) {
			throw new MduException(e, "Exception loading properties %s.", propsFileName);
		} finally {
			try {
				propsStream.close();
			} catch (Exception e) { /* ignore */ }
		}

		return props;
	} // end loadProps(String, Class<?>)

} // end class MdUtil
