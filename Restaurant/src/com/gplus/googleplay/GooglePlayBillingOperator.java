package com.gplus.googleplay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jp.tamtam.nekomeshiya.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.gplus.googleplay.Constants.CommonKey;
import com.gplus.googleplay.util.CommonTools;
import com.gplus.googleplay.util.IabHelper;
import com.gplus.googleplay.util.IabHelper.OnConsumeFinishedListener;
import com.gplus.googleplay.util.IabHelper.OnIabPurchaseFinishedListener;
import com.gplus.googleplay.util.IabResult;
import com.gplus.googleplay.util.Inventory;
import com.gplus.googleplay.util.Purchase;
import com.gplus.googleplay.util.SkuDetails;

public class GooglePlayBillingOperator extends Handler {
	public static final String LOG_TAG = "GooglePlayBillingOperator";
	
	private static final String Base64EncodedPublicKey="MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArhqhFW6SxeBvw9a8KvTabIrJPRzhwrPgbFGCPBy+0gio5x4yCLGAML6MhcAuF9DeRH5zn7fEyNfOZyXWrMK0nLCNsFUcRJRh6KKCQkAtllo/WJuID5IA8TlNfqoJ2HGu2Jx81sN+ki4fwPay1WdYLM5s7cXQQhxhWdMRKVXk96s7HG8vpofkPsdqJTGZomfEPItETsPAva4LspFw1I/LfMDpgciTvGBUnz/lKjD6BFNV44EUPh+QCxFqqwTfkoRfARgGf9+Eli8a0yVZo2I/NPXx3UqK3CnI6sIgsRBo44+qVpKvd3GeIeVNDOK50gXmNLNarNNJL9MUBjzYb1eRXwIDAQAB";
	
	private Context mContext; 
	private IabHelper mIabHelper;
	private static Inventory mInventory;
	private List<String> mSkuList;
//	private OpenSDKOperator mOperator;
	public PayInfo mPayInfo;
	private List<JSONObject> mCacheServerGoods;
	private boolean mIsIabSetup = false;
	private boolean mIsSetupIabFinished = true;
	private int mInventoryCheckingCbId = -1000;
	
	public static final int GOOGLE_PURCHASE_REQUEST_CODE = 10001;
	private final static int MSG_POST_EXEC_PURCHASE = 100;
	private final static int MSG_POST_EXEC_LOAD_INVENTORY = 101;
	private final static int MSG_DELAY_EXEC_PURCASE_UNTIL_IAB_ALREADY = 102;
	private final static int MSG_DELAY_EXEC_LOAD_INVENTORY_UNTIL_IAB_ALREADY = 103;
	private final static int MSG_DISMISS_PROGRESS = 104;
	private final static int MSG_DELAY_CHECK_INVENTORY = 105;
	private final static int DELAY_TIME = 1000 * 60;
	
	public interface PurchaseListener {
		public void OnPurchase (boolean isSucc);
	}
	
	private PurchaseListener mPurchaseListener = null;
	
	public GooglePlayBillingOperator(Context context/*, OpenSDKOperator operator*/) {
		super ();
		mContext = context;
		mIabHelper = new IabHelper(context, Base64EncodedPublicKey);
//		mOperator = operator;
		mIabHelper.enableDebugLogging(true);
		mSkuList = new ArrayList<String>();
	}
	
	public void init() {
		mIsIabSetup = false;
		mIsSetupIabFinished = false;
		try {
			mIabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener(){
				
				@Override
				public void onIabSetupFinished(IabResult result) {
					if (!result.isSuccess()) {
						Log.e(LOG_TAG, "Problem setting up In-app Billing: " + result);
	 				} else {
	 					Log.e(LOG_TAG, "Set up seccess.");
						mIsIabSetup = true;
	 				}
					mIsSetupIabFinished = true;
					GooglePlayBillingOperator handler = GooglePlayBillingOperator.this;
					if (handler.hasMessages(MSG_DELAY_EXEC_LOAD_INVENTORY_UNTIL_IAB_ALREADY)) {
						handler.removeMessages(MSG_DELAY_EXEC_LOAD_INVENTORY_UNTIL_IAB_ALREADY);
						handler.sendEmptyMessage(MSG_DELAY_EXEC_LOAD_INVENTORY_UNTIL_IAB_ALREADY);
					}
					else
					if (handler.hasMessages(MSG_DELAY_EXEC_PURCASE_UNTIL_IAB_ALREADY)) {
						handler.removeMessages(MSG_DELAY_EXEC_PURCASE_UNTIL_IAB_ALREADY);
						handler.sendEmptyMessage(MSG_DELAY_EXEC_PURCASE_UNTIL_IAB_ALREADY);
					} else {
						//handler.sendEmptyMessage(MSG_DELAY_EXEC_LOAD_INVENTORY_UNTIL_IAB_ALREADY);
					}
				}}
			);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void pay(final PayInfo payInfo, PurchaseListener listener) {
		mPurchaseListener = listener;
		
//		getProductList();
		CommonTools.showProgress(mContext, ProgressDialog.STYLE_SPINNER);
		mPayInfo = payInfo;
		Log.e(LOG_TAG, "GooglePlayBillingOperator: " + mIsSetupIabFinished);
		if (mIsSetupIabFinished) {
			sendEmptyMessage(MSG_POST_EXEC_PURCHASE);
		} else {
			sendEmptyMessageDelayed(MSG_DELAY_EXEC_PURCASE_UNTIL_IAB_ALREADY, DELAY_TIME);
		}
	}
	
	public void getProducts() {
		Log.e(LOG_TAG, "internl get products.");
		
	}
	
	public void loadInventory() {
		if (mIsSetupIabFinished) {
			sendEmptyMessage(MSG_POST_EXEC_LOAD_INVENTORY);
		} else {
			sendEmptyMessageDelayed(MSG_DELAY_EXEC_LOAD_INVENTORY_UNTIL_IAB_ALREADY, DELAY_TIME);
		}
	}
	
	public void checkInventory(int cbId, String data) {
		Log.e(LOG_TAG, "checkInventory: " + data);
		mInventoryCheckingCbId = cbId;
		if (mCacheServerGoods == null) {
			mCacheServerGoods = new ArrayList<JSONObject>();
		}
		
		try {
			JSONObject jsonData = new JSONObject(data);
			JSONObject goodsData = jsonData.getJSONObject("goods");
			for (Iterator iter = goodsData.keys(); iter.hasNext();) { 
				String key = (String)iter.next(); 
				JSONArray array = goodsData.getJSONArray(key); 
				
				for (int i = 0; i < array.length(); i++) {
					JSONObject item = array.getJSONObject(i);
					mCacheServerGoods.add(item);
					String sku = item.getString("paykey");
					if (!TextUtils.isEmpty(sku)) {
						mSkuList.add(sku);
					}
				}
			}
//			mCacheServerGoods = goodsData;
			loadInventory();
			sendEmptyMessageDelayed(MSG_DELAY_CHECK_INVENTORY, DELAY_TIME);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void checkInventory (String data) {
		Log.e(LOG_TAG, "checkInventory: " + data);
		if (TextUtils.isEmpty(data))
			return ;
		
		try {
			JSONObject jsonData = new JSONObject(data);
			JSONArray goodIdsData = jsonData.getJSONArray("productIds");
			for (int i = 0; i < goodIdsData.length(); i++) {
				String id = goodIdsData.getString(i);
				
				if (!TextUtils.isEmpty(id)) {
					mSkuList.add(id);
				}
				
				loadInventory();
				sendEmptyMessageDelayed(MSG_DELAY_CHECK_INVENTORY, DELAY_TIME);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	private void delayCheckInventory() {
		/*
		Log.e(LOG_TAG, "delay check inventory.");
		JSONObject resultData = new JSONObject();
		try {
			if (mInventory == null) {
				Log.e(LOG_TAG, "Empty inventory");
				resultData.put(CommonKey.STATUS, 0);
				//ExternalCall.instance.callUnity(mInventoryCheckingCbId, resultData.toString());
				return ;
			}
			JSONArray array = new JSONArray();
			for (int i = 0; i < mCacheServerGoods.size(); i++) {
				JSONObject serverGood = mCacheServerGoods.get(i);
				String sku = serverGood.optString("paykey");
				JSONObject product = new JSONObject();
				if (mInventory.hasDetails(sku)) {
					SkuDetails details = mInventory.getSkuDetails(sku);
					product.put("productId", details.getSku());
					product.put("name", details.getTitle().split(" \\(")[0]);
					
					String desc = details.getDescription();
					if (!TextUtils.isEmpty(desc)) {
						if (desc.endsWith("\\.")) {
							desc = desc.substring(0, desc.length() - 1);
						}
					}
					product.put("description", desc);
					
					String priceStr = details.getPrice();
					Locale locale = mContext.getResources().getConfiguration().locale;
					NumberFormat format = DecimalFormat.getInstance(locale);
					
					String symbol = Currency.getInstance(locale).getSymbol();
					float purePrice = 0.0f;

					if (priceStr.contains(symbol)) {
						priceStr = priceStr.replace(symbol, " ");
					    Pattern matchsip = Pattern.compile("[a-zA-Z]");
					    Matcher mp = matchsip.matcher(priceStr);
					    priceStr = mp.replaceAll("");
						purePrice = format.parse(priceStr.trim()).floatValue();
					} else {
					    Pattern matchsip = Pattern.compile("[a-zA-Z]");
					    Matcher mp = matchsip.matcher(priceStr);
					    priceStr = mp.replaceAll("");
					    
						String[] strs = priceStr.split(" ");
						for (String string : strs) {
							try {
								purePrice = format.parse(string).floatValue();
							} catch (Exception e){
								// ignore 
							}
						}
						
						if (purePrice == 0.0f) {
							Log.e(LOG_TAG, "Can not parse avaliable price.(Src Price String:" + priceStr +")");
//							throw new Exception("Can not parse avaliable price.(Src Price String:" + priceStr +")");
						}
					}
					
					
					float sPrice = format.parse(serverGood.optString("money")).floatValue();
					float sOldMoney = format.parse(serverGood.optString("omoney")).floatValue();
					float oldMoney = purePrice / sPrice * sOldMoney;
					String sIcon = serverGood.optString("icon");
					String sCurrency = serverGood.optString("currency");
					product.put("icon", sIcon);
					product.put("price", String.valueOf(purePrice));
					product.put("omoney", new DecimalFormat("#.00").format(oldMoney));
					product.put("currency", sCurrency);
					product.put("symbol", symbol);
					array.put(product);
				}
			}
			resultData.put(CommonKey.STATUS, 1);
			resultData.put("goods",array);
			//ExternalCall.instance.callUnity(mInventoryCheckingCbId, resultData.toString());
		} catch (Exception e) {
			e.printStackTrace();
			//ExternalCall.instance.callUnity(mInventoryCheckingCbId, "");
		}
		*/
	}
	
	private void internalPay() {
		Log.e(LOG_TAG, "internal pay =>" + mPayInfo.getProductId());
		if (mIabHelper.isAsyncInProgress()) {
			GooglePlayBillingOperator.this.sendEmptyMessage(MSG_DISMISS_PROGRESS);
			Log.e(LOG_TAG, "Abort this operation, Other async is processing");
			return ;
		}
		
		if (mInventory == null) {
			GooglePlayBillingOperator.this.sendEmptyMessage(MSG_DISMISS_PROGRESS);
			
			if (mPurchaseListener != null)
				mPurchaseListener.OnPurchase(false);
			
			return ;
		}
		mIabHelper.launchPurchaseFlow((Activity) mContext,
				mPayInfo.getProductId(), GOOGLE_PURCHASE_REQUEST_CODE,
				new OnIabPurchaseFinishedListener() {
			
					@Override
					public void onIabPurchaseFinished(IabResult result,
							final Purchase info) {
						int response = result.getResponse();
						Log.e(LOG_TAG, "Purchase finish. success?:" + result.isSuccess());
						if (result.isFailure()) {
							Log.e(LOG_TAG, "Error when purchasing:" + result.toString());
							if (response == IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED) {
//								mIabHelper.consumeAsync(info, null);
								showToast(mContext.getString(R.string.pay_user_cancel));
							} else if (response == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
								((Activity) mContext).runOnUiThread(new Runnable() {

									@Override
									public void run() {
										if (mInventory.hasPurchase(mPayInfo.getProductId())) {
											mIabHelper.consumeAsync(mInventory.getPurchase(mPayInfo.getProductId()),null);
										} else {
											if (info != null) {
												mIabHelper.consumeAsync(info, null);
											}
										}
									}
								});
								showToast("Purchase failed: " + result.getMessage());
							} else{
								showToast("Purchase failed: " + result.getMessage());
							}
							// TODO
							if (mPurchaseListener != null)
								mPurchaseListener.OnPurchase(false);
							GooglePlayBillingOperator.this.sendEmptyMessage(MSG_DISMISS_PROGRESS);
 						} else if (result.isSuccess()) {
							if (response == IabHelper.BILLING_RESPONSE_RESULT_OK) {
								if (info.getSku().equals(mPayInfo.getProductId())) {									
									final Purchase purchase = info;
									((Activity)mContext).runOnUiThread(new Runnable(){

										@Override
										public void run() {
											mIabHelper.consumeAsync(purchase,
												new OnConsumeFinishedListener() {
			
													@Override
													public void onConsumeFinished(
															Purchase purchase,
															IabResult result) {
														Log.e(LOG_TAG, "Consume Finished.");
														GooglePlayBillingOperator.this.sendEmptyMessage(MSG_DISMISS_PROGRESS);
														if (mPurchaseListener != null)
															mPurchaseListener.OnPurchase(true);
//														ExternalCall.instance.sdkPayFinish(mOperator
//																.createPayCbDataToUnity(true, mPayInfo));
														
//														try {
//															// There is no need for pay checking when use googleplay billing
//															// so here notify Unity pay result to refresh user golds.
//															mOperator.notifyUnityPayResult(true, mPayInfo);
//														} catch (JSONException e) {
//															e.printStackTrace();
//														}
													}
												}
											);
										}
										
									});
								}
							}
 						}
						
					}
				});
	}
	
	private void showToast(final String msg) {
		((Activity)mContext).runOnUiThread(new Runnable() {

			@Override
			public void run() {
				Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
			}
		});
	}
	
	public void onPurchaseActivityOnResult(final int requestCode, final int resultCode, final Intent data) {
		Log.e(LOG_TAG, "google purchase activity on result => " + requestCode + "|" + resultCode);
		if (requestCode == GOOGLE_PURCHASE_REQUEST_CODE) {
			new Thread(new Runnable() {
	
				public void run() {
					if (!mIabHelper.handleActivityResult(requestCode, resultCode, data)) {
					} else {
						Log.e(LOG_TAG, "onActivityResult handled by IABUtil.");
					}
				}
			}).start();
		}
	}
	
	/**
	 *  Note: Remember call this when destroy
	 */
	public void dispose() {
		if (mIabHelper != null) {
			mIabHelper.dispose();
			mIabHelper = null;
		}
	}
	
	public void queryInventory() {
		Log.e(LOG_TAG, "queryInventory.");
		if (mIabHelper.isAsyncInProgress()) {
			Log.e(LOG_TAG, "Abort this operation, Other async is processing");
			return ;
		}
		try {
			mIabHelper.queryInventoryAsync(true, mSkuList, new IabHelper.QueryInventoryFinishedListener() {
				
				@Override
				public void onQueryInventoryFinished(IabResult result, Inventory inv) {
					Log.e(LOG_TAG, "query inventory finished:" + result.toString());
					if (result.isSuccess()) {
						mInventory = inv;
						Log.e(LOG_TAG, "Get Inventory Succeed");
						if (mInventory == null) {
							Log.e(LOG_TAG, "inventory is null.");
						}						
					}
					
					if (hasMessages(MSG_DELAY_CHECK_INVENTORY)) {
						removeMessages(MSG_DELAY_CHECK_INVENTORY);
						sendEmptyMessage(MSG_DELAY_CHECK_INVENTORY);
					}
				}
			});
		} catch (IllegalStateException exception) {
			exception.printStackTrace();
		}
	}
	
	private void showNotSupportGoogleBillingDialog() {
		showToast(mContext.getString(R.string.device_not_support_google_billing));
	}
	
	private JSONObject getProductsJsonString(boolean status, List<SkuDetails> products) {
		JSONObject result = new JSONObject();
		try {
			result.put(CommonKey.STATUS, status ? 1 : 0);
			if (products != null) {
				JSONArray array = new JSONArray();
				for (SkuDetails skuDetails : products) {
					array.put(skuDetails.toString());
				}
				result.put("items", array);
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	public void failedSetupIab() {
		Log.e(LOG_TAG, "failed setup iab.");
		CommonTools.dismissProgress();
	}
	
	public void handleMessage(Message msg) {
		Log.e(LOG_TAG, "handler message:" + msg.what);
		switch(msg.what) {
		case MSG_POST_EXEC_LOAD_INVENTORY:
			if (mIsIabSetup) {
				queryInventory();
			} else {
				if(this.hasMessages(MSG_DELAY_CHECK_INVENTORY)) {
					this.removeMessages(MSG_DELAY_CHECK_INVENTORY);
					delayCheckInventory();
				}
				failedSetupIab();
			}
			break;
		case MSG_POST_EXEC_PURCHASE:
			if (mIsIabSetup) {
				internalPay();
			} else {
				failedSetupIab();
			}
			break;
		case MSG_DELAY_EXEC_LOAD_INVENTORY_UNTIL_IAB_ALREADY:
			sendEmptyMessage(MSG_POST_EXEC_LOAD_INVENTORY);
			break;
		case MSG_DELAY_EXEC_PURCASE_UNTIL_IAB_ALREADY:
			sendEmptyMessage(MSG_POST_EXEC_PURCHASE);
			break;
		case MSG_DISMISS_PROGRESS:
			CommonTools.dismissProgress();
			break;
		case MSG_DELAY_CHECK_INVENTORY:
			delayCheckInventory();
			break;
		default:
			break;
		}
	}
}
