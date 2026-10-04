// GENERATED no-op defaults for providers that do not implement every SignInService call.
package com.liskovsoft.smartyoutubetv2.common.providers.stub;

import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import io.reactivex.Observable;
import java.util.List;

import java.util.Collections;

public class StubSignInService implements SignInService {
    @Override
    public boolean isSigned() { return false; }

    @Override
    public List<Account> getAccounts() { return Collections.emptyList(); }

    @Override
    public Account getSelectedAccount() { return null; }

    @Override
    public void addOnAccountChange(OnAccountChange listener) {  }

    @Override
    public void selectAccount(Account account) {  }

    @Override
    public void removeAccount(Account account) {  }

    @Override
    public String printDebugInfo() { return null; }

    @Override
    public Observable<String> signInObserve() { return Observable.empty(); }

}
