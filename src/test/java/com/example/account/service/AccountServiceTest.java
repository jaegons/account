package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.dto.AccountDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.type.AccountStatus;
import com.example.account.type.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountUserRepository accountUserRepository;
    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccountSuccess() {
        //given

        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();


        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.findFirstByOrderByIdDesc())
                .willReturn(Optional.of(Account.builder()
                        .accountUser(user)
                        .accountNumber("1000000012").build()));

        given(accountRepository.save(any()))
                .willReturn(Account.builder()
                        .accountUser(user)
                        .accountNumber("1000000015").build());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        //when

        AccountDto accountDto = accountService.createAccount(1L, 1000L);
        //then

        verify(accountRepository, times(1)).save(captor.capture());

        assertEquals(12L, accountDto.getUserId());
        assertEquals("1000000013", captor.getValue().getAccountNumber());

    }

    @Test
    void createFirstAccount() {
        //given

        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();


        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.findFirstByOrderByIdDesc())
                .willReturn(Optional.empty());

        given(accountRepository.save(any()))
                .willReturn(Account.builder()
                        .accountUser(user)
                        .accountNumber("1000000015").build());

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        //when
        AccountDto accountDto = accountService.createAccount(1L, 1000L);
        //then

        verify(accountRepository, times(1)).save(captor.capture());

        assertEquals(12L, accountDto.getUserId());
        assertEquals("1000000000", captor.getValue().getAccountNumber());


    }

    @Test
    @DisplayName("해당 유저 없음 - 계좌 생성 실패")
    void createAccount_UserNotFound() {
        //given
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        //when
        AccountException accountException = assertThrows(AccountException.class, () -> accountService.createAccount(1L, 1000L));

        //then
        assertEquals(ErrorCode.USER_NOT_FOUND, accountException.getErrorCode());

    }

    @Test
    @DisplayName("10개 초과 계좌 생성시 실패")
    void createAccount_maxAccountIs10() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.countByAccountUser(any())).willReturn(10);

        //when
        AccountException accountException = assertThrows(AccountException.class, () -> accountService.createAccount(1L, 1000L));

        //then
        assertEquals(ErrorCode.USER_ACCOUNT_PER_USER_10, accountException.getErrorCode());

    }

    @Test
    void successDeleteAccount() {
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(user)
                        .balance(0L)
                        .accountNumber("1000000012").build()));


        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        //when

        AccountDto accountDto = accountService.deleteAccount(1L, "1000000012");
        //then
        verify(accountRepository, times(1)).save(captor.capture());

        assertEquals(12L, accountDto.getUserId());
        assertEquals("1000000012", captor.getValue().getAccountNumber());
        assertEquals(AccountStatus.UNREGISTERED, captor.getValue().getAccountStatus());

    }

    @Test
    @DisplayName("해당 유저 없음 - 계좌 해지 실패")
    void deleteAccount_UserNotFound() {

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        //when
        AccountException accountException = assertThrows(AccountException.class, () -> accountService.deleteAccount(1L, "100000000"));

        //then

        assertEquals(accountException.getErrorCode(), ErrorCode.USER_NOT_FOUND);

    }

    @Test
    @DisplayName("해당 계좌 없음 - 계좌 해지 실패")
    void deleteAccount_AccountNotFound() {

        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        //when
        AccountException accountException = assertThrows(AccountException.class, () -> accountService.deleteAccount(1L, "100000000"));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.ACCOUNT_NOT_FOUND);

    }


    @Test
    @DisplayName("계좌 소유주 다름 - 계좌 해지 실패")
    void deleteAccount_UserAccountUnMatched() {

        AccountUser pobi = AccountUser.builder()
                .id(12L).name("pobi").build();

        AccountUser harry = AccountUser.builder()
                .id(13L).name("harry").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder().balance(0L).accountUser(harry).accountNumber("123456789").build()));

        //when
        AccountException accountException = assertThrows(AccountException.class, () -> accountService.deleteAccount(1L, "123456789"));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.USER_ACCOUNT_UNMATCHED);

    }

    @Test
    @DisplayName("해지 계좌는 잔액이 없어야 한다")
    void deleteAccount_BalanceNotEmpty() {

        AccountUser pobi = AccountUser.builder()
                .id(12L).name("pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder().balance(100L).accountUser(pobi).accountNumber("123456789").build()));

        //when
        AccountException accountException = assertThrows(AccountException.class, () -> accountService.deleteAccount(1L, "123456789"));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.BALANCE_NOT_EMPTY);

    }

    @Test
    @DisplayName("해지 계좌는 해지할 수 없다")
    void deleteAccount_AccountAlreadyUnRegistered() {

        AccountUser pobi = AccountUser.builder()
                .id(12L).name("pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder().balance(100L)
                        .accountUser(pobi)
                        .accountStatus(AccountStatus.UNREGISTERED)
                        .accountNumber("123456789")
                        .build()));

        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> accountService.deleteAccount(1L, "123456789"));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.ACCOUNT_ALREADY_UNREGISTERED);

    }

    @Test
    void successGetAccountByUserId() {

        AccountUser pobi = AccountUser.builder()
                .id(12L).name("pobi").build();
        List<Account> accounts = List.of(
                Account.builder().accountUser(pobi).accountNumber("1111111111").balance(1000L).build(),
                Account.builder().accountUser(pobi).accountNumber("2222222222").balance(2000L).build(),
                Account.builder().accountUser(pobi).accountNumber("3333333333").balance(3000L).build()
        );
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));
        given(accountRepository.findAccountByAccountUser(any()))
                .willReturn(accounts);

        //when

        List<AccountDto> accountDtoList = accountService.getAccountsByUserId(1L);
        //then
        assertEquals(3,accountDtoList.size());

        assertEquals("1111111111",accountDtoList.get(0).getAccountNumber());
        assertEquals(1000L,accountDtoList.get(0).getBalance());

        assertEquals("2222222222",accountDtoList.get(1).getAccountNumber());
        assertEquals(2000L,accountDtoList.get(1).getBalance());

        assertEquals("3333333333",accountDtoList.get(2).getAccountNumber());
        assertEquals(3000L,accountDtoList.get(2).getBalance());

    }
    @Test
    void failedToGetAccountByUserId() {


        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> accountService.getAccountsByUserId(1L));

        //then
        assertEquals(accountException.getErrorCode(),ErrorCode.USER_NOT_FOUND);



    }



}