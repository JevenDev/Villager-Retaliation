package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.storage.StorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class StorageScreen extends AbstractContainerScreen<StorageMenu> {
    private static final ResourceLocation TEXTURE=ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    public StorageScreen(StorageMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title); imageWidth=menu.pages()>1?208:176; imageHeight=222; inventoryLabelY=128;
    }
    @Override protected void renderBg(GuiGraphics g,float tick,int x,int y) {
        g.blit(TEXTURE,leftPos,topPos,0,0,176,125);
        g.blit(TEXTURE,leftPos,topPos+125,0,126,176,97);
        if(menu.pages()==1) return;
        g.fill(leftPos+176,topPos,leftPos+208,topPos+222,0xffc6c6c6);
        g.fill(leftPos+179,topPos+18,leftPos+189,topPos+126,0xff373737);
        int thumb=topPos+18+menu.page*94/(menu.pages()-1);
        g.fill(leftPos+180,thumb,leftPos+188,thumb+14,0xffaaaaaa);
        g.drawString(font,(menu.page+1)+"/"+menu.pages(),leftPos+178,topPos+132,0xff373737,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float tick) { renderBackground(g,x,y,tick); super.render(g,x,y,tick); renderTooltip(g,x,y); }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(menu.pages()==1) return super.mouseScrolled(x,y,horizontal,vertical);
        menu.page=Math.max(0,Math.min(menu.pages()-1,menu.page-(int)Math.signum(vertical))); return true;
    }
    private void scroll(double y) { menu.page=Math.max(0,Math.min(menu.pages()-1,(int)Math.round((y-topPos-25)/94*(menu.pages()-1)))); }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(menu.pages()>1 && button==0 && x>=leftPos+176 && x<leftPos+194 && y>=topPos+18 && y<topPos+126) { scroll(y); return true; }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(menu.pages()>1 && button==0 && x>=leftPos+176 && x<leftPos+194) { scroll(y); return true; }
        return super.mouseDragged(x,y,button,dx,dy);
    }
}
