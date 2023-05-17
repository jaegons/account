package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.domain.Transaction;
import com.example.account.dto.TransactionDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.repository.TransactionRepository;
import com.example.account.type.AccountStatus;
import com.example.account.type.ErrorCode;
import com.example.account.type.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.account.type.TransactionResultType.F;
import static com.example.account.type.TransactionResultType.S;
import static com.example.account.type.TransactionType.USE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    AccountUserRepository accountUserRepository;
    @InjectMocks
    private TransactionService transactionService;

    @Test
    void successUseBalance() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();


        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000000").build();

        given(accountRepository.findByAccountNumber(any()))
                .willReturn(Optional.of(account));

        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionId("transactionId")
                        .amount(1000L)
                        .balanceSnapShot(9000L)
                        .transactedAt(LocalDateTime.now())
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        //when
        TransactionDto transactionDto = transactionService.useBalance(1L, "1000000000", 200L);

        //then
        verify(transactionRepository, times(1)).save(captor.capture());

        assertEquals(200L, captor.getValue().getAmount());
        assertEquals(9800L, captor.getValue().getBalanceSnapShot());

        assertEquals(9000L, transactionDto.getBalanceSnapShot());
        assertEquals(USE, transactionDto.getTransactionType());
        assertEquals(S, transactionDto.getTransactionResult());
        assertEquals(1000L, transactionDto.getAmount());

    }

    @Test
    @DisplayName("해당 유저 없음 - 잔액 사용 실패")
    void useBalance_UserNotFound() {
        //given
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L));

        //then
        assertEquals(ErrorCode.USER_NOT_FOUND, accountException.getErrorCode());

    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 실패")
    void useBalance_AccountNotFound() {

        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());


        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L));

        //then

        assertEquals(accountException.getErrorCode(), ErrorCode.ACCOUNT_NOT_FOUND);

    }

    @Test
    @DisplayName("계좌 소유주 다름 - 잔액 사용 실패")
    void useBalance_UserAccountUnMatched() {

        AccountUser pobi = AccountUser.builder()
                .id(12L).name("pobi").build();

        AccountUser harry = AccountUser.builder()
                .id(13L).name("harry").build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder().balance(0L).accountUser(harry).accountNumber("123456789").build()));

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L));

        //then

        assertEquals(accountException.getErrorCode(), ErrorCode.USER_ACCOUNT_UNMATCHED);
    }

    @Test
    @DisplayName("해지 계좌는 사용할 수 없다")
    void useBalance_AccountAlreadyUnRegistered() {

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
                () -> transactionService.useBalance(1L, "1000000000", 1000L));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.ACCOUNT_ALREADY_UNREGISTERED);

    }

    @Test
    @DisplayName("거래 금액이 잔액보다 큰 경우")
    void failToUseBalance() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(100L)
                .accountNumber("1000000012")
                .build();


        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L));

        //then
        assertEquals(ErrorCode.AMOUNT_EXCEED_BALANCE, accountException.getErrorCode());

        verify(transactionRepository, times(0)).save(any());

    }

    @Test
    @DisplayName("실패 트랜잭션 저장 성공")
    void saveFailUseTransaction() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000000").build();

        given(accountRepository.findByAccountNumber(any()))
                .willReturn(Optional.of(account));

        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionId("transactionId")
                        .amount(1000L)
                        .balanceSnapShot(9000L)
                        .transactedAt(LocalDateTime.now())
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        //when
        transactionService.saveFailedUseTransaction("1000000000", 200L);

        //then
        verify(transactionRepository, times(1)).save(captor.capture());

        assertEquals(200L, captor.getValue().getAmount());
        assertEquals(10000L, captor.getValue().getBalanceSnapShot());
        assertEquals(F, captor.getValue().getTransactionResultType());


    }

    @Test
    void successCancelBalance() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();


        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000000").build();

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionIdForCancel")
                .amount(200L)
                .balanceSnapShot(9800L)
                .transactedAt(LocalDateTime.now())
                .build();


        given(accountRepository.findByAccountNumber(any()))
                .willReturn(Optional.of(account));


        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(TransactionType.CANCEL)
                        .transactionResultType(S)
                        .transactionId("transactionIdForCancel")
                        .amount(200L)
                        .balanceSnapShot(10000L)
                        .transactedAt(LocalDateTime.now())
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        //when
        TransactionDto transactionDto = transactionService.cancelBalance("transactionIdForCancel", "1000000000", 200L);

        //then
        verify(transactionRepository, times(1)).save(captor.capture());

        assertEquals(200L, captor.getValue().getAmount());
        assertEquals(10000L + 200L, captor.getValue().getBalanceSnapShot());

        assertEquals(10000L, transactionDto.getBalanceSnapShot());
        assertEquals(TransactionType.CANCEL, transactionDto.getTransactionType());
        assertEquals(S, transactionDto.getTransactionResult());
        assertEquals(200L, transactionDto.getAmount());

    }


    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 취소 실패")
    void cancelBalance_AccountNotFound() {


        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(Transaction.builder()
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionId("transactionIdForCancel")
                        .amount(200L)
                        .balanceSnapShot(9800L)
                        .transactedAt(LocalDateTime.now())
                        .build()));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());


        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("1000000000", "1000000000", 1000L));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.ACCOUNT_NOT_FOUND);

    }

    @Test
    @DisplayName("해당 거래 없음 - 잔액 사용 취소 실패")
    void cancelBalance_TransactionNotFound() {


        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.empty());


        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("1000000000", "1000000000", 1000L));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.TRANSACTION_NOT_FOUND);

    }

    @Test
    @DisplayName("거래와 계좌가 매칭 실패 - 잔액 사용 취소 실패")
    void cancelBalance_TransactionAccountUnMatched() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();


        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000000").build();

        Account accountNotUse = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000001").build();

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionIdForCancel")
                .amount(200L)
                .balanceSnapShot(9800L)
                .transactedAt(LocalDateTime.now())
                .build();


        given(accountRepository.findByAccountNumber(any()))
                .willReturn(Optional.of(accountNotUse));

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionIdForCancel", "1000000000", 200L));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.TRANSACTION_ACCOUNT_UNMATCHED);

    }

    @Test
    @DisplayName("거래금액과 취소금액이 다름 - 잔액 사용 취소 실패")
    void cancelBalance_CancelMustFully() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();


        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000000").build();


        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionIdForCancel")
                .amount(200L + 1000L)
                .balanceSnapShot(9800L)
                .transactedAt(LocalDateTime.now())
                .build();


        given(accountRepository.findByAccountNumber(any()))
                .willReturn(Optional.of(account));

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionIdForCancel", "1000000000", 200L));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.CANCEL_MUST_FULLY);

    }

    @Test
    @DisplayName("취소는 1년까지만 가능 - 잔액 사용 취소 실패")
    void cancelBalance_TooOldOrderToCancel() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();


        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000000").build();


        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionIdForCancel")
                .amount(200L)
                .balanceSnapShot(9800L)
                .transactedAt(LocalDateTime.now().minusYears(1))
                .build();


        given(accountRepository.findByAccountNumber(any()))
                .willReturn(Optional.of(account));

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        //when
        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionIdForCancel", "1000000000", 200L));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.TOO_OLD_ORDER_TO_CANCEL);

    }

    @Test
    void successQueryTransaction() {
        //given
        AccountUser user = AccountUser.builder()
                .id(12L).name("pobi").build();


        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(AccountStatus.IN_USE)
                .balance(10000L)
                .accountNumber("1000000000").build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .amount(200L)
                .balanceSnapShot(9800L)
                .transactedAt(LocalDateTime.now())
                .build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        //when

        TransactionDto transactionDto = transactionService.queryTransaction("trxId");

        //then

        assertEquals(USE,transactionDto.getTransactionType());
        assertEquals(S,transactionDto.getTransactionResult());
        assertEquals(200L,transactionDto.getAmount());
        assertEquals(9800L,transactionDto.getBalanceSnapShot());
        assertEquals("transactionId",transactionDto.getTransactionId());
    }

    @Test
    @DisplayName("원거래 없음 - 거래 조회 실패")
    void queryTransaction_TransactionNotFound() {
        //given

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.empty());

        //when

        AccountException accountException = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("1000000000", "1000000000", 1000L));

        //then
        assertEquals(accountException.getErrorCode(), ErrorCode.TRANSACTION_NOT_FOUND);

    }


}