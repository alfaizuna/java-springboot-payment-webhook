package com.alfaizunawebid.baseapp.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.alfaizunawebid.baseapp.dto.ChangePasswordRequest;
import com.alfaizunawebid.baseapp.dto.UpdateUserRequest;
import com.alfaizunawebid.baseapp.dto.UserResponse;
import com.alfaizunawebid.baseapp.model.User;
import com.alfaizunawebid.baseapp.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Mengambil entitas User yang sedang terautentikasi dari SecurityContextHolder
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("User is not authenticated");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    /**
     * Mengambil profil UserResponse milik user yang sedang login
     */
    public UserResponse getCurrentUserProfile() {
        User currentUser = getCurrentUser();
        return UserResponse.fromUser(currentUser);
    }

    /**
     * Memperbarui profil (nama) user yang sedang login
     */
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        User currentUser = getCurrentUser();
        currentUser.setName(request.getName());
        User updatedUser = userRepository.save(currentUser);
        return UserResponse.fromUser(updatedUser);
    }

    /**
     * Mengubah password user yang sedang login
     */
    public void changePassword(ChangePasswordRequest request) {
        User currentUser = getCurrentUser();

        // 1. Validasi password lama
        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            throw new IllegalArgumentException("Current password does not match");
        }

        // 2. Validasi kesesuaian newPassword & confirmationPassword
        if (request.getConfirmationPassword() != null && !request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new IllegalArgumentException("New password and confirmation password do not match");
        }

        // 3. Update password dengan hash baru
        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);
    }

    /**
     * Mengambil daftar semua user dengan pagination (Khusus ADMIN)
     */
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::fromUser);
    }

    /**
     * Menghapus user berdasarkan ID (Khusus ADMIN)
     */
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
}
