package com.xyron.game.launcher.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.xyron.game.R;
import com.xyron.game.launcher.util.ButtonAnimator;

import java.util.ArrayList;
import java.util.List;

public class DonateFragment extends Fragment {

    private EditText amountField;
    private TextView selectedOfferSummary;
    private View donateButton;
    private final List<View> offerCards = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_donate, container, false);

        amountField = view.findViewById(R.id.donate_summa);
        selectedOfferSummary = view.findViewById(R.id.selected_offer_summary);
        donateButton = view.findViewById(R.id.button_donate);

        bindOfferCard(view.findViewById(R.id.offer_card_start), "50", "Pacote Inicial");
        bindOfferCard(view.findViewById(R.id.offer_card_plus), "100", "Pacote Plus");
        bindOfferCard(view.findViewById(R.id.offer_card_gold), "250", "Pacote Gold");
        bindOfferCard(view.findViewById(R.id.offer_card_elite), "500", "Pacote Elite");

        if (donateButton != null) {
            donateButton.setOnTouchListener(new ButtonAnimator(requireContext(), donateButton));
            donateButton.setOnClickListener(v -> continueToShop());
        }

        selectOffer(0, "50", "Pacote Inicial");
        return view;
    }

    private void bindOfferCard(View card, String amount, String label) {
        if (card == null) {
            return;
        }

        final int cardIndex = offerCards.size();
        offerCards.add(card);
        card.setOnTouchListener(new ButtonAnimator(requireContext(), card));
        card.setOnClickListener(v -> selectOffer(cardIndex, amount, label));
    }

    private void selectOffer(int selectedIndex, String amount, String label) {
        if (amountField != null) {
            amountField.setText(amount);
        }
        if (selectedOfferSummary != null) {
            selectedOfferSummary.setText(label + " selecionado - valor base " + amount);
        }

        for (int i = 0; i < offerCards.size(); i++) {
            View card = offerCards.get(i);
            card.setBackgroundResource(i == selectedIndex
                    ? R.drawable.store_offer_card_selected
                    : R.drawable.store_offer_card_default);
            card.setAlpha(i == selectedIndex ? 1.0f : 0.94f);
        }
    }

    private void continueToShop() {
        if (getContext() == null) {
            return;
        }

        String amount = amountField == null || amountField.getText() == null
                ? ""
                : amountField.getText().toString().trim();
        if (amount.isEmpty()) {
            Toast.makeText(getContext(), "Escolha um pacote ou informe um valor.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://samp-mobile.com/shop/"));
        startActivity(browserIntent);
    }
}
